//! Download integrity for files the launcher installs.
//!
//! Every managed file is checked against a hash published by its source (Mojang
//! and Fabric metadata, Modrinth, or a hash pinned in this binary). Results are
//! cached by path, size and modification time so unchanged files are not hashed
//! again on every launch; a changed file, or a different expected hash, is hashed
//! again. The cache is a performance aid, not a security boundary: anything that
//! can rewrite files and timestamps as this user can also rewrite the cache.

use serde::{Deserialize, Serialize};
use sha1::Sha1;
use sha2::{Digest, Sha256, Sha512};
use std::collections::HashMap;
use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::UNIX_EPOCH;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Algorithm {
    Sha1,
    Sha256,
    Sha512,
}

impl Algorithm {
    fn name(self) -> &'static str {
        match self {
            Algorithm::Sha1 => "sha1",
            Algorithm::Sha256 => "sha256",
            Algorithm::Sha512 => "sha512",
        }
    }

    fn hex_len(self) -> usize {
        match self {
            Algorithm::Sha1 => 40,
            Algorithm::Sha256 => 64,
            Algorithm::Sha512 => 128,
        }
    }
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Expected {
    pub algorithm: Algorithm,
    pub digest: String,
    pub size: Option<u64>,
}

impl Expected {
    /// None when the metadata has no usable hash of this kind.
    pub fn new(algorithm: Algorithm, digest: &str, size: Option<u64>) -> Option<Self> {
        let digest = digest.trim().to_ascii_lowercase();
        (digest.len() == algorithm.hex_len() && digest.bytes().all(|byte| byte.is_ascii_hexdigit()))
            .then_some(Self {
                algorithm,
                digest,
                size: size.filter(|size| *size > 0),
            })
    }
}

pub fn file_digest(path: &Path, algorithm: Algorithm) -> Result<String, String> {
    let mut file = File::open(path).map_err(|error| error.to_string())?;
    let mut buffer = vec![0u8; 64 * 1024];
    macro_rules! stream {
        ($hasher:expr) => {{
            let mut hasher = $hasher;
            loop {
                let read = file.read(&mut buffer).map_err(|error| error.to_string())?;
                if read == 0 {
                    break;
                }
                hasher.update(&buffer[..read]);
            }
            hex(&hasher.finalize())
        }};
    }
    Ok(match algorithm {
        Algorithm::Sha1 => stream!(Sha1::new()),
        Algorithm::Sha256 => stream!(Sha256::new()),
        Algorithm::Sha512 => stream!(Sha512::new()),
    })
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|byte| format!("{byte:02x}")).collect()
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
struct Entry {
    size: u64,
    modified: u128,
    algorithm: String,
    digest: String,
}

#[derive(Default)]
pub struct VerifiedFiles {
    store: Option<PathBuf>,
    entries: HashMap<String, Entry>,
    dirty: bool,
}

impl VerifiedFiles {
    pub fn load(store: PathBuf) -> Self {
        let entries = fs::read(&store)
            .ok()
            .and_then(|bytes| serde_json::from_slice::<HashMap<String, Entry>>(&bytes).ok())
            .unwrap_or_default();
        Self {
            store: Some(store),
            entries,
            dirty: false,
        }
    }

    pub fn in_memory() -> Self {
        Self::default()
    }

    /// Writes the cache if it changed. A failed write only costs a re-hash later.
    pub fn save(&mut self) {
        let Some(store) = self.store.clone() else {
            return;
        };
        if !self.dirty {
            return;
        }
        if let Some(parent) = store.parent() {
            let _ = fs::create_dir_all(parent);
        }
        let Ok(bytes) = serde_json::to_vec(&self.entries) else {
            return;
        };
        let staging = store.with_extension("json.part");
        if fs::write(&staging, bytes).is_ok() && fs::rename(&staging, &store).is_ok() {
            self.dirty = false;
        } else {
            let _ = fs::remove_file(&staging);
        }
    }

    fn state(path: &Path) -> Option<(u64, u128)> {
        let metadata = fs::metadata(path).ok()?;
        if !metadata.is_file() {
            return None;
        }
        let modified = metadata
            .modified()
            .ok()?
            .duration_since(UNIX_EPOCH)
            .ok()?
            .as_nanos();
        Some((metadata.len(), modified))
    }

    fn key(path: &Path) -> String {
        path.to_string_lossy().to_string()
    }

    fn cached(&self, path: &Path, expected: &Expected, state: (u64, u128)) -> bool {
        self.entries.get(&Self::key(path)).is_some_and(|entry| {
            entry.size == state.0
                && entry.modified == state.1
                && entry.algorithm == expected.algorithm.name()
                && entry.digest == expected.digest
        })
    }

    fn remember(&mut self, path: &Path, expected: &Expected, state: (u64, u128)) {
        self.entries.insert(
            Self::key(path),
            Entry {
                size: state.0,
                modified: state.1,
                algorithm: expected.algorithm.name().to_string(),
                digest: expected.digest.clone(),
            },
        );
        self.dirty = true;
    }

    /// Whether the file is unchanged since it last matched any recorded hash.
    pub fn unchanged_since_verified(&self, path: &Path) -> bool {
        let Some(state) = Self::state(path) else {
            return false;
        };
        self.entries
            .get(&Self::key(path))
            .is_some_and(|entry| entry.size == state.0 && entry.modified == state.1)
    }

    pub fn forget(&mut self, path: &Path) {
        if self.entries.remove(&Self::key(path)).is_some() {
            self.dirty = true;
        }
    }
}

/// Whether `path` exists and matches `expected`. Hashes only when the cache cannot
/// vouch for the current file.
pub fn matches(cache: &Mutex<VerifiedFiles>, path: &Path, expected: &Expected) -> Result<bool, String> {
    let Some(state) = VerifiedFiles::state(path) else {
        return Ok(false);
    };
    if expected.size.is_some_and(|size| size != state.0) {
        return Ok(false);
    }
    if cache
        .lock()
        .map_err(|_| "Integrity cache is unavailable.".to_string())?
        .cached(path, expected, state)
    {
        return Ok(true);
    }
    let actual = file_digest(path, expected.algorithm)?;
    if actual != expected.digest {
        return Ok(false);
    }
    // Re-read the state: the file must not have changed while it was hashed.
    if VerifiedFiles::state(path) != Some(state) {
        return Ok(false);
    }
    cache
        .lock()
        .map_err(|_| "Integrity cache is unavailable.".to_string())?
        .remember(path, expected, state);
    Ok(true)
}

/// Whether the cache already vouches for this file and hash, without hashing.
pub fn vouched(cache: &Mutex<VerifiedFiles>, path: &Path, expected: &Expected) -> bool {
    let Some(state) = VerifiedFiles::state(path) else {
        return false;
    };
    cache
        .lock()
        .map(|cache| cache.cached(path, expected, state))
        .unwrap_or(false)
}

/// Ensures `path` holds the expected bytes: keeps a matching file, otherwise
/// removes it and downloads again. Without a published hash an existing file is
/// kept as before. A download that does not match is deleted and fails the launch.
pub fn ensure_file<F>(
    cache: &Mutex<VerifiedFiles>,
    url: &str,
    path: &Path,
    expected: Option<&Expected>,
    label: &str,
    download: F,
) -> Result<(), String>
where
    F: Fn(&str, &Path) -> Result<(), String>,
{
    if path.is_file() {
        match expected {
            None => return Ok(()),
            Some(expected) => {
                if matches(cache, path, expected)? {
                    return Ok(());
                }
                fs::remove_file(path).map_err(|error| {
                    format!("{label} failed its integrity check and could not be replaced: {error}")
                })?;
                if let Ok(mut cache) = cache.lock() {
                    cache.forget(path);
                }
            }
        }
    }
    if url.trim().is_empty() {
        return Err(format!("No download URL for {label}."));
    }
    download(url, path)?;
    if let Some(expected) = expected {
        if !matches(cache, path, expected)? {
            let _ = fs::remove_file(path);
            return Err(format!(
                "{label} did not match its published {} hash after download, so it was deleted. Try again; if this repeats, your connection may be altering downloads.",
                expected.algorithm.name().to_ascii_uppercase()
            ));
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};

    fn temp_dir(name: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!("gamble-integrity-{name}-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    fn sha1_of(bytes: &[u8]) -> String {
        hex(&Sha1::digest(bytes))
    }

    #[test]
    fn expected_rejects_malformed_digests() {
        assert!(Expected::new(Algorithm::Sha1, "abc", None).is_none());
        assert!(Expected::new(Algorithm::Sha1, &"z".repeat(40), None).is_none());
        assert!(Expected::new(Algorithm::Sha256, &"a".repeat(40), None).is_none());
        let expected = Expected::new(Algorithm::Sha512, &"A".repeat(128), Some(0)).unwrap();
        assert_eq!(expected.digest, "a".repeat(128));
        assert_eq!(expected.size, None);
    }

    #[test]
    fn keeps_matching_files_and_replaces_tampered_ones() {
        let dir = temp_dir("replace");
        let path = dir.join("lib.jar");
        let good = b"genuine library bytes".to_vec();
        let expected = Expected::new(Algorithm::Sha1, &sha1_of(&good), Some(good.len() as u64)).unwrap();
        let cache = Mutex::new(VerifiedFiles::in_memory());
        let downloads = AtomicUsize::new(0);
        let fetch = |_: &str, target: &Path| {
            downloads.fetch_add(1, Ordering::SeqCst);
            fs::write(target, &good).map_err(|error| error.to_string())
        };

        ensure_file(&cache, "https://example.test/lib.jar", &path, Some(&expected), "lib", fetch).unwrap();
        assert_eq!(downloads.load(Ordering::SeqCst), 1);
        ensure_file(&cache, "https://example.test/lib.jar", &path, Some(&expected), "lib", fetch).unwrap();
        assert_eq!(downloads.load(Ordering::SeqCst), 1, "a verified file is kept");

        fs::write(&path, b"tampered library bytes").unwrap();
        ensure_file(&cache, "https://example.test/lib.jar", &path, Some(&expected), "lib", fetch).unwrap();
        assert_eq!(downloads.load(Ordering::SeqCst), 2, "a changed file is fetched again");
        assert_eq!(fs::read(&path).unwrap(), good);
    }

    #[test]
    fn a_bad_download_is_deleted_and_fails() {
        let dir = temp_dir("bad");
        let path = dir.join("asset");
        let expected = Expected::new(Algorithm::Sha1, &sha1_of(b"expected"), None).unwrap();
        let cache = Mutex::new(VerifiedFiles::in_memory());
        let error = ensure_file(&cache, "https://example.test/a", &path, Some(&expected), "asset", |_, target| {
            fs::write(target, b"something else").map_err(|error| error.to_string())
        })
        .unwrap_err();
        assert!(error.contains("did not match"), "{error}");
        assert!(!path.exists());
    }

    #[test]
    fn without_a_published_hash_an_existing_file_is_kept() {
        let dir = temp_dir("nohash");
        let path = dir.join("file");
        fs::write(&path, b"kept").unwrap();
        let cache = Mutex::new(VerifiedFiles::in_memory());
        ensure_file(&cache, "", &path, None, "file", |_, _| panic!("must not download")).unwrap();
        assert_eq!(fs::read(&path).unwrap(), b"kept");
    }

    #[test]
    fn cache_round_trips_and_notices_changes() {
        let dir = temp_dir("cache");
        let store = dir.join("verified.json");
        let path = dir.join("file");
        fs::write(&path, b"cached bytes").unwrap();
        let expected = Expected::new(Algorithm::Sha1, &sha1_of(b"cached bytes"), None).unwrap();
        {
            let cache = Mutex::new(VerifiedFiles::load(store.clone()));
            assert!(matches(&cache, &path, &expected).unwrap());
            cache.lock().unwrap().save();
        }
        let cache = Mutex::new(VerifiedFiles::load(store));
        assert!(cache.lock().unwrap().unchanged_since_verified(&path));
        std::thread::sleep(std::time::Duration::from_millis(20));
        fs::write(&path, b"changed bytes").unwrap();
        assert!(!cache.lock().unwrap().unchanged_since_verified(&path));
        assert!(!matches(&cache, &path, &expected).unwrap());
    }

    #[test]
    fn digests_match_known_vectors() {
        let dir = temp_dir("vectors");
        let path = dir.join("abc");
        fs::write(&path, b"abc").unwrap();
        assert_eq!(file_digest(&path, Algorithm::Sha1).unwrap(), "a9993e364706816aba3e25717850c26c9cd0d89d");
        assert_eq!(
            file_digest(&path, Algorithm::Sha256).unwrap(),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
        assert!(file_digest(&path, Algorithm::Sha512).unwrap().starts_with("ddaf35a193617aba"));
    }
}
