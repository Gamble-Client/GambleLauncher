use super::*;

#[test]
fn failed_spawn_exit_confirmation_is_bounded_and_accepts_only_confirmed_exit() {
    use std::time::{Duration, Instant};
    for inaccessible in [false, true] {
        let started = Instant::now();
        let mut polls = 0;
        assert!(!confirm_exit(
            || {
                polls += 1;
                if inaccessible {
                    Err(io::Error::new(
                        io::ErrorKind::PermissionDenied,
                        "unconfirmed child",
                    ))
                } else {
                    Ok(false)
                }
            },
            Duration::from_millis(60)
        ));
        assert!(started.elapsed() < Duration::from_secs(1));
        assert!(polls <= 5);
    }
    assert!(confirm_exit(|| Ok(true), Duration::from_secs(3)));
}

fn folder() -> StagedDirectory {
    StagedDirectory::new(&std::env::temp_dir().join("gamble-test-target")).unwrap()
}

struct Stream {
    remaining: usize,
    chunk: usize,
    read: usize,
    fail_at_end: bool,
}
impl Read for Stream {
    fn read(&mut self, bytes: &mut [u8]) -> io::Result<usize> {
        if self.remaining == 0 && self.fail_at_end {
            return Err(io::Error::other("simulated broken stream"));
        }
        let count = self.remaining.min(self.chunk).min(bytes.len());
        bytes[..count].fill(b'x');
        self.remaining -= count;
        self.read += count;
        Ok(count)
    }
}
fn stream(size: usize) -> Stream {
    Stream {
        remaining: size,
        chunk: 3,
        read: 0,
        fail_at_end: false,
    }
}

#[test]
fn bounded_copy_probes_only_one_byte_and_never_writes_over_limit() {
    let mut input = stream(1_000_000);
    let mut output = Vec::new();
    assert!(copy_bounded(&mut input, &mut output, 16).is_err());
    assert_eq!(input.read, 17);
    assert!(output.len() <= 16);
}

#[test]
fn metadata_limit_ignores_false_small_and_missing_claims() {
    for declared in [None, Some(1)] {
        let mut input = stream(1_000_000);
        assert!(read_bounded(&mut input, declared, 16).is_err());
        assert_eq!(input.read, 17);
    }
    let mut input = stream(10);
    assert!(read_bounded(&mut input, Some(u64::MAX), 16).is_err());
    assert_eq!(input.read, 0);
}

#[test]
fn bounded_read_accepts_empty_and_exact_valid_metadata() {
    assert!(read_bounded(io::empty(), None, 0).unwrap().is_empty());
    assert_eq!(
        read_bounded(stream(16), Some(16), 16).unwrap(),
        vec![b'x'; 16]
    );
    assert_eq!(copy_bounded(io::empty(), io::sink(), 0).unwrap(), 0);
}

#[test]
fn downloads_reject_status_empty_false_lengths_and_cleanup_failures() {
    let root = folder();
    let target = root.path().join("existing.jar");
    fs::write(&target, b"previous").unwrap();
    for (status, actual, declared, broken) in [
        (302, 8, Some(8), false),
        (404, 8, None, false),
        (200, 0, None, false),
        (204, 0, Some(0), false),
        (200, 12, Some(2), false),
        (200, 2, Some(12), false),
        (200, 40, Some(1), false),
        (200, 40, None, false),
        (200, 1, Some(u64::MAX), false),
        (200, 8, None, true),
    ] {
        let mut input = stream(actual);
        input.fail_at_end = broken;
        assert!(stage_download(input, status, declared, &target, 16, true).is_err());
        assert_eq!(fs::read(&target).unwrap(), b"previous");
        assert_eq!(fs::read_dir(root.path()).unwrap().count(), 1);
    }
}

#[test]
fn valid_download_is_staged_and_drop_cleans_it_without_touching_target() {
    let root = folder();
    let target = root.path().join("download.jar");
    for declared in [None, Some(16)] {
        let staged = stage_download(stream(16), 200, declared, &target, 16, false).unwrap();
        assert_eq!(fs::read(staged.path()).unwrap(), vec![b'x'; 16]);
        assert!(!target.exists());
        let path = staged.path().to_owned();
        drop(staged);
        assert!(!path.exists());
    }
}

#[test]
fn exclusive_creation_refuses_existing_files() {
    let root = folder();
    let path = root.path().join("occupied");
    fs::write(&path, b"untouched").unwrap();
    assert!(create_exclusive(&path, true).is_err());
    assert_eq!(fs::read(path).unwrap(), b"untouched");
}

#[cfg(unix)]
#[test]
fn private_permissions_exist_before_first_byte_and_symlinks_are_not_followed() {
    use std::os::unix::fs::{symlink, PermissionsExt};
    let root = folder();
    let target = root.path().join("credential");
    let staged = StagedFile::new(&target, true).unwrap();
    assert_eq!(fs::metadata(staged.path()).unwrap().len(), 0);
    assert_eq!(
        fs::metadata(staged.path()).unwrap().permissions().mode() & 0o777,
        0o600
    );
    symlink(staged.path(), &target).unwrap();
    assert!(create_exclusive(&target, true).is_err());
    assert_eq!(fs::metadata(staged.path()).unwrap().len(), 0);
}

#[test]
fn credentials_replace_privately_without_staging_leftovers() {
    let root = folder();
    let target = root.path().join("credential.json");
    for content in [b"first".as_slice(), b"second"] {
        super::super::write_private_file(&target, content).unwrap();
        assert_eq!(fs::read(&target).unwrap(), content);
        assert_eq!(fs::read_dir(root.path()).unwrap().count(), 1);
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            assert_eq!(
                fs::metadata(&target).unwrap().permissions().mode() & 0o777,
                0o600
            );
        }
    }
}

#[test]
fn java_escaping_covers_empty_quotes_slashes_and_control_characters() {
    assert_eq!(
        java_argfile(&[String::new(), "a b\\\"\n\r\t\u{c}'#@".to_string()]).unwrap(),
        b"\"\"\n\"a b\\\\\\\"\\n\\r\\t\\f'#@\"\n"
    );
    assert!(java_argfile(&["bad\0argument".to_string()]).is_err());
}

#[test]
fn java_byte_escaping_protects_cp932_trailing_backslashes_after_encoding() {
    // 表 (95 5c) and ソ (83 5c), including immediately before a closing quote.
    for (native, expected) in [
        (b"\x95\x5c".as_slice(), b"\"\x95\\\\\"\n".as_slice()),
        (
            b"C:\\\x95\x5c\\\x83\x5c".as_slice(),
            b"\"C:\\\\\x95\\\\\\\\\x83\\\\\"\n".as_slice(),
        ),
    ] {
        let mut encoded = Vec::new();
        quote_java_argument_bytes(native, &mut encoded).unwrap();
        assert_eq!(encoded, expected);
    }
}

#[test]
fn java_spawn_failure_removes_private_argument_file() {
    let root = folder();
    let command = Command::new(root.path().join("nonexistent-java"));
    assert!(PrivateChild::spawn(command, &["fake-bearer".into()], root.path()).is_err());
    assert_eq!(
        fs::read_dir(root.path().join(".java-arguments"))
            .unwrap()
            .count(),
        0
    );
}

#[test]
fn java21_round_trips_argfile_and_hides_arguments_until_exit_cleanup() {
    use base64::{engine::general_purpose::STANDARD, Engine};
    use std::process::Stdio;
    let root = folder();
    let source = root.path().join("ArgEcho.java");
    fs::write(&source, br#"import java.util.Base64;
import java.nio.charset.StandardCharsets;
class ArgEcho { public static void main(String[] args) throws Exception {
 for (String a: args) System.out.println(Base64.getEncoder().encodeToString(a.getBytes(StandardCharsets.UTF_8)));
 System.out.flush(); System.in.read();
}}
"#).unwrap();
    let mut values = vec![
        "",
        "space here",
        "\"quotes\"'",
        "C:\\with spaces\\tail\\",
        "\n\r\t\u{c}",
        "#comment",
        "@missing-argfile",
        "fake-bearer-sensitive",
    ]
    .into_iter()
    .map(str::to_string)
    .collect::<Vec<_>>();
    #[cfg(not(windows))]
    values.push("日本語 🦊".into());
    #[cfg(windows)]
    if java_argfile(&["日本語 🦊".into()]).is_ok() {
        values.push("日本語 🦊".into());
    }
    let mut args = vec![source.to_string_lossy().into_owned()];
    args.extend(values.iter().cloned());
    let mut command = Command::new("java");
    command
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::inherit());
    let mut child = PrivateChild::spawn(command, &args, root.path())
        .expect("Java 21 must be installed for the launcher argument-file integration test");
    let staged = child.arguments.as_ref().unwrap().path().to_owned();
    assert!(staged.exists());
    // Read the echoed lines before inspecting argv: the Java executable may be
    // reached through a distribution wrapper. The fixture then waits on stdin.
    let stdout = child.child.stdout.take().unwrap();
    let mut output = std::io::BufReader::new(stdout);
    let mut actual = Vec::new();
    for _ in &values {
        let mut line = String::new();
        use std::io::BufRead;
        assert!(output.read_line(&mut line).unwrap() > 0);
        actual.push(line.trim_end_matches(['\r', '\n']).to_string());
    }
    #[cfg(target_os = "linux")]
    {
        let argv = fs::read(format!("/proc/{}/cmdline", child.id())).unwrap();
        assert!(!argv
            .windows(b"fake-bearer-sensitive".len())
            .any(|part| part == b"fake-bearer-sensitive"));
        assert!(argv
            .split(|byte| *byte == 0)
            .any(|arg| arg.starts_with(b"@")));
    }
    child.child.stdin.take().unwrap().write_all(b"\n").unwrap();
    assert!(child.wait().unwrap().success());
    assert!(!staged.exists());
    assert_eq!(
        actual,
        values
            .iter()
            .map(|arg| STANDARD.encode(arg.as_bytes()))
            .collect::<Vec<_>>()
    );
}

#[test]
fn java_try_wait_removes_argument_file_after_exit() {
    use std::{
        process::Stdio,
        time::{Duration, Instant},
    };
    let root = folder();
    let mut command = Command::new("java");
    command.stdout(Stdio::null()).stderr(Stdio::null());
    let mut child = PrivateChild::spawn(command, &["-version".into()], root.path()).unwrap();
    let path = child.arguments.as_ref().unwrap().path().to_owned();
    let deadline = Instant::now() + Duration::from_secs(10);
    loop {
        if let Some(status) = child.try_wait().unwrap() {
            assert!(status.success());
            assert!(!path.exists());
            break;
        }
        if Instant::now() >= deadline {
            child.kill().unwrap();
            child.wait().unwrap();
            panic!("Java did not exit in time");
        }
        std::thread::sleep(Duration::from_millis(10));
    }
}

#[cfg(windows)]
#[test]
fn windows_argfile_encoding_is_lossless_or_rejected() {
    use windows_sys::Win32::Globalization::{GetACP, MultiByteToWideChar};
    for value in ["plain ASCII", "日本語 🦊", "é", "𝄞", "¥", "表", "ソ"] {
        if let Ok(encoded) = windows_private::encode_argfile(value) {
            unsafe {
                let size = MultiByteToWideChar(
                    GetACP(),
                    0,
                    encoded.as_ptr(),
                    encoded.len() as i32,
                    std::ptr::null_mut(),
                    0,
                );
                assert!(size > 0);
                let mut decoded = vec![0u16; size as usize];
                assert_eq!(
                    MultiByteToWideChar(
                        GetACP(),
                        0,
                        encoded.as_ptr(),
                        encoded.len() as i32,
                        decoded.as_mut_ptr(),
                        size
                    ),
                    size
                );
                assert_eq!(String::from_utf16(&decoded).unwrap(), value);
            }
        } else {
            assert!(!value.is_ascii());
        }
    }
}

#[cfg(windows)]
#[test]
fn windows_private_file_has_only_current_token_user_in_protected_dacl_before_bytes() {
    use std::{os::windows::ffi::OsStrExt, ptr};
    use windows_sys::Win32::{
        Foundation::LocalFree,
        Security::{
            Authorization::{
                ConvertSecurityDescriptorToStringSecurityDescriptorW, GetNamedSecurityInfoW,
                SE_FILE_OBJECT,
            },
            DACL_SECURITY_INFORMATION, OWNER_SECURITY_INFORMATION,
        },
    };
    let root = folder();
    let file = StagedFile::new(&root.path().join("secret"), true).unwrap();
    assert_eq!(fs::metadata(file.path()).unwrap().len(), 0);
    let name: Vec<u16> = file
        .path()
        .as_os_str()
        .encode_wide()
        .chain(Some(0))
        .collect();
    unsafe {
        let mut descriptor = ptr::null_mut();
        let info = DACL_SECURITY_INFORMATION | OWNER_SECURITY_INFORMATION;
        assert_eq!(
            GetNamedSecurityInfoW(
                name.as_ptr(),
                SE_FILE_OBJECT,
                info,
                ptr::null_mut(),
                ptr::null_mut(),
                ptr::null_mut(),
                ptr::null_mut(),
                &mut descriptor
            ),
            0
        );
        let mut sddl = ptr::null_mut();
        assert_ne!(
            ConvertSecurityDescriptorToStringSecurityDescriptorW(
                descriptor,
                1,
                info,
                &mut sddl,
                ptr::null_mut()
            ),
            0
        );
        let mut len = 0;
        while *sddl.add(len) != 0 {
            len += 1;
        }
        let actual = String::from_utf16_lossy(std::slice::from_raw_parts(sddl, len));
        LocalFree(sddl.cast());
        LocalFree(descriptor);
        let sid = windows_private::current_user_sid().unwrap();
        assert_eq!(actual, format!("O:{sid}D:P(A;;FA;;;{sid})"));
    }
}
