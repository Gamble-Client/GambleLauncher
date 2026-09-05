//! Recovery for this native launcher's marked Java argument files only.
//! Normal child exit removes them immediately. Abrupt launcher exit leaves private
//! files until the next startup/Java launch; unknown process state is never guessed.
use super::*;
use serde::{Deserialize, Serialize};
use std::io::BufRead;

const DIRECTORY: &str = ".java-arguments";
const PREFIX: &str = "native-java-v1-";
const MARKER: &str = "# gamble-native-java-arguments-v1 ";
const RECORD_MARKER: &str = "gamble-native-java-child-v1";
const RECORD_LIMIT: u64 = 2048;

#[derive(Serialize, Deserialize)]
struct ProcessRef {
    pid: u32,
    identity: Option<String>,
}

#[derive(Debug, PartialEq)]
enum ProcessState {
    Running(String),
    Gone,
    Unknown,
}

#[derive(Serialize, Deserialize)]
struct Header {
    nonce: String,
}

#[derive(Serialize, Deserialize)]
struct ChildRecord {
    marker: String,
    nonce: String,
    child: ProcessRef,
}

fn process_ref(pid: u32) -> ProcessRef {
    ProcessRef {
        pid,
        identity: match process_state(pid) {
            ProcessState::Running(identity) => Some(identity),
            _ => None,
        },
    }
}

fn definitely_finished(reference: &ProcessRef, state: ProcessState) -> bool {
    // A reused PID is deliberately retained, as are missing start identities.
    // Never use an age threshold to guess whether Java has opened the file yet.
    reference.pid != 0
        && reference
            .identity
            .as_ref()
            .is_some_and(|identity| valid_identity(identity))
        && state == ProcessState::Gone
}

fn valid_identity(identity: &str) -> bool {
    #[cfg(target_os = "linux")]
    if let Some(value) = identity.strip_prefix("linux:") {
        return value.split_once(':').is_some_and(|(boot, ticks)| {
            boot.len() == 36
                && boot
                    .bytes()
                    .all(|byte| byte.is_ascii_hexdigit() || byte == b'-')
                && ticks.parse::<u64>().is_ok()
        });
    }
    #[cfg(windows)]
    if let Some(value) = identity.strip_prefix("windows:") {
        return value.parse::<u64>().is_ok_and(|value| value != 0);
    }
    false
}

#[cfg(target_os = "linux")]
fn process_state(pid: u32) -> ProcessState {
    if pid == 0 {
        return ProcessState::Unknown;
    }
    // Boot ID prevents a reboot plus PID/start-tick reuse from matching an old child.
    // Prove procfs is available before interpreting ENOENT as process termination.
    let boot = match fs::read_to_string("/proc/sys/kernel/random/boot_id") {
        Ok(boot) if boot.trim().len() == 36 => boot,
        _ => return ProcessState::Unknown,
    };
    if fs::read_to_string("/proc/self/stat").is_err() {
        return ProcessState::Unknown;
    }
    let stat = match fs::read_to_string(format!("/proc/{pid}/stat")) {
        Ok(stat) => stat,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return ProcessState::Gone,
        Err(_) => return ProcessState::Unknown,
    };
    linux_stat_identity(&stat, boot.trim())
        .map(ProcessState::Running)
        .unwrap_or(ProcessState::Unknown)
}

#[cfg(any(target_os = "linux", test))]
fn linux_stat_identity(stat: &str, boot: &str) -> Option<String> {
    // comm may contain spaces and ')'; the last ')' precedes field 3 (state).
    let fields = stat
        .rsplit_once(')')?
        .1
        .split_whitespace()
        .collect::<Vec<_>>();
    let ticks = fields.get(19)?.parse::<u64>().ok()?;
    Some(format!("linux:{boot}:{ticks}"))
}

#[cfg(windows)]
fn process_state(pid: u32) -> ProcessState {
    super::windows_private::process_identity(pid).map_or_else(
        |_| ProcessState::Unknown,
        |identity| identity.map_or(ProcessState::Gone, ProcessState::Running),
    )
}

#[cfg(not(any(target_os = "linux", windows)))]
fn process_state(_: u32) -> ProcessState {
    ProcessState::Unknown
}

#[cfg(unix)]
fn owned_private(path: &Path, directory: bool) -> bool {
    use std::os::unix::fs::MetadataExt;
    unsafe extern "C" {
        fn geteuid() -> u32;
    }
    let Ok(metadata) = fs::symlink_metadata(path) else {
        return false;
    };
    // SAFETY: geteuid has no inputs and returns this process's effective owner.
    metadata.uid() == unsafe { geteuid() }
        && metadata.mode() & 0o077 == 0
        && if directory {
            metadata.is_dir()
        } else {
            metadata.is_file() && metadata.nlink() == 1
        }
}

#[cfg(windows)]
fn owned_private(path: &Path, directory: bool) -> bool {
    use std::os::windows::fs::MetadataExt;
    use windows_sys::Win32::Storage::FileSystem::FILE_ATTRIBUTE_REPARSE_POINT;
    let Ok(metadata) = fs::symlink_metadata(path) else {
        return false;
    };
    metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT == 0
        && if directory {
            metadata.is_dir()
        } else {
            metadata.is_file()
        }
        && super::windows_private::has_private_acl(path).unwrap_or(false)
}

#[cfg(not(any(unix, windows)))]
fn owned_private(_: &Path, _: bool) -> bool {
    false
}

fn ensure_directory(folder: &Path) -> io::Result<PathBuf> {
    fs::create_dir_all(folder)?;
    let directory = folder.join(DIRECTORY);
    #[cfg(windows)]
    let result = super::windows_private::create_directory(&directory);
    #[cfg(not(windows))]
    let result = {
        let mut builder = fs::DirBuilder::new();
        #[cfg(unix)]
        {
            use std::os::unix::fs::DirBuilderExt;
            builder.mode(0o700);
        }
        builder.create(&directory)
    };
    if let Err(error) = result {
        if error.kind() != io::ErrorKind::AlreadyExists {
            return Err(error);
        }
    }
    if !owned_private(&directory, true) {
        return Err(io::Error::new(
            io::ErrorKind::PermissionDenied,
            "Java argument directory is not owned and private.",
        ));
    }
    Ok(directory)
}

pub(super) struct JavaArguments {
    file: StagedFile,
    record: Option<StagedFile>,
    nonce: String,
}

impl JavaArguments {
    pub(super) fn retain_for_recovery(self) {
        // All handles are already closed. Retain marked files, not a live watcher.
        std::mem::forget(self);
    }
    pub(super) fn new(folder: &Path, encoded: &[u8]) -> io::Result<Self> {
        let directory = ensure_directory(folder)?;
        // Best-effort cleanup cannot prevent a new launch because an old child has
        // inaccessible lifetime information or an unrelated file is present.
        let _ = cleanup_argument_files(folder);
        for _ in 0..16 {
            let nonce = super::super::random_base64_url(24);
            let path = directory.join(format!("{PREFIX}{nonce}.args"));
            let file = match create_exclusive(&path, true) {
                Ok(file) => file,
                Err(error) if error.kind() == io::ErrorKind::AlreadyExists => continue,
                Err(error) => return Err(error),
            };
            let mut file = StagedFile {
                path,
                file: Some(file),
            };
            let header = Header {
                nonce: nonce.clone(),
            };
            // Marker is committed before sensitive bytes. A partial marker cannot
            // contain credentials; a complete marker lets the next run recover them.
            writeln!(file.writer(), "{MARKER}{}", serde_json::to_string(&header)?)?;
            file.writer().sync_all()?;
            file.writer().write_all(encoded)?;
            file.close()?;
            let path = file.path().with_extension("child");
            let mut record = StagedFile {
                file: Some(create_exclusive(&path, true)?),
                path,
            };
            let data = ChildRecord {
                marker: RECORD_MARKER.into(),
                nonce: nonce.clone(),
                child: ProcessRef {
                    pid: 0,
                    identity: None,
                },
            };
            serde_json::to_writer(record.writer(), &data)?;
            record.writer().sync_all()?;
            record.close()?;
            return Ok(Self {
                file,
                record: Some(record),
                nonce,
            });
        }
        Err(io::Error::new(
            io::ErrorKind::AlreadyExists,
            "Could not allocate a private Java argument file.",
        ))
    }

    pub(super) fn path(&self) -> &Path {
        self.file.path()
    }

    pub(super) fn record_child(&mut self, pid: u32) -> io::Result<()> {
        let data = ChildRecord {
            marker: RECORD_MARKER.into(),
            nonce: self.nonce.clone(),
            child: process_ref(pid),
        };
        let bytes = serde_json::to_vec(&data)?;
        let target = self.record.as_ref().expect("pre-spawn sidecar").path();
        let mut staging = StagedFile::new(target, true)?;
        staging.writer().write_all(&bytes)?;
        staging.writer().sync_all()?;
        staging.close()?;
        // Same-directory rename replaces the complete sidecar in one operation;
        // readers see the pre-spawn record or the committed child record, not halves.
        fs::rename(staging.path(), target)
    }
}

/// Never scans ordinary .part files or the Java sibling's records. Return a count
/// of argument files removed, excluding their non-secret child-identity sidecars.
pub fn cleanup_argument_files(folder: &Path) -> io::Result<usize> {
    cleanup_with(folder, process_state)
}

fn cleanup_with<F: Fn(u32) -> ProcessState>(folder: &Path, probe: F) -> io::Result<usize> {
    let directory = folder.join(DIRECTORY);
    if !directory.try_exists()? {
        return Ok(0);
    }
    if !owned_private(&directory, true) {
        return Ok(0);
    }
    let mut removed = 0;
    for entry in fs::read_dir(&directory)?.take(512) {
        let entry = entry?;
        let name = entry.file_name();
        let Some(name) = name.to_str() else {
            continue;
        };
        let Some(nonce) = name
            .strip_prefix(PREFIX)
            .and_then(|name| name.strip_suffix(".args"))
        else {
            continue;
        };
        if nonce.len() != 32
            || !nonce
                .bytes()
                .all(|byte| byte.is_ascii_alphanumeric() || byte == b'-' || byte == b'_')
        {
            continue;
        }
        let path = entry.path();
        if !owned_private(&path, false) {
            continue;
        }
        let file = match File::open(&path) {
            Ok(file) => file,
            Err(_) => continue,
        };
        let mut header = Vec::new();
        if io::BufReader::new(file.take(RECORD_LIMIT))
            .read_until(b'\n', &mut header)
            .is_err()
            || header.last() != Some(&b'\n')
        {
            continue;
        }
        let Some(data) = header.strip_prefix(MARKER.as_bytes()) else {
            continue;
        };
        let header: Header = match serde_json::from_slice(data) {
            Ok(header) => header,
            Err(_) => continue,
        };
        if header.nonce != nonce {
            continue;
        }
        let record_path = path.with_extension("child");
        let record_exists = fs::symlink_metadata(&record_path).is_ok();
        // Unowned/symlinked sidecars are uncertainty, never permission to unlink.
        if record_exists && !owned_private(&record_path, false) {
            continue;
        }
        let record: Option<ChildRecord> = File::open(&record_path)
            .ok()
            .and_then(|file| read_bounded(file, None, RECORD_LIMIT).ok())
            .and_then(|bytes| serde_json::from_slice(&bytes).ok());
        let finished = if let Some(record) = record {
            if record.marker != RECORD_MARKER || record.nonce != nonce || record.child.pid == 0 {
                continue;
            }
            definitely_finished(&record.child, probe(record.child.pid))
        } else {
            false
        };
        if finished {
            if fs::remove_file(&path).is_ok() {
                removed += 1;
                if record_exists {
                    let _ = fs::remove_file(&record_path);
                }
            }
        }
    }
    Ok(removed)
}

#[cfg(test)]
#[path = "java_arguments_tests.rs"]
mod tests;
