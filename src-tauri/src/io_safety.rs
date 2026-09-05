//! Bounded streams and exclusively created staging files. No network policy lives here.
use std::{
    fs::{self, File, OpenOptions},
    io::{self, Read, Write},
    path::{Path, PathBuf},
    process::{Child, Command, ExitStatus},
};

#[path = "java_arguments.rs"]
mod java_arguments;
pub use java_arguments::cleanup_argument_files;
use java_arguments::JavaArguments;

fn invalid(message: &str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message)
}

/// Probe at most one byte beyond the budget, without writing that byte.
pub fn copy_bounded<R: Read, W: Write>(
    mut reader: R,
    mut writer: W,
    limit: u64,
) -> io::Result<u64> {
    let mut total = 0u64;
    let mut buffer = [0u8; 16 * 1024];
    loop {
        let remaining = limit - total;
        let request = remaining.saturating_add(1).min(buffer.len() as u64) as usize;
        let read = match reader.read(&mut buffer[..request]) {
            Err(error) if error.kind() == io::ErrorKind::Interrupted => continue,
            result => result?,
        };
        if read == 0 {
            return Ok(total);
        }
        if read as u64 > remaining {
            return Err(invalid("Stream exceeds its byte safety limit."));
        }
        writer.write_all(&buffer[..read])?;
        total += read as u64;
    }
}

pub fn read_bounded<R: Read>(reader: R, declared: Option<u64>, limit: u64) -> io::Result<Vec<u8>> {
    check_declared_size(declared, limit)?;
    // Never preallocate from an untrusted declared length.
    let mut bytes = Vec::new();
    copy_bounded(reader, &mut bytes, limit)?;
    Ok(bytes)
}

fn check_declared_size(declared: Option<u64>, limit: u64) -> io::Result<()> {
    if declared.is_some_and(|size| size > limit) {
        return Err(invalid("Declared size exceeds its byte safety limit."));
    }
    Ok(())
}

pub struct StagedFile {
    path: PathBuf,
    file: Option<File>,
}

impl StagedFile {
    pub fn new(target: &Path, private: bool) -> io::Result<Self> {
        let parent = target.parent().unwrap_or_else(|| Path::new("."));
        fs::create_dir_all(parent)?;
        for _ in 0..16 {
            let path = parent.join(format!(".gamble-{}.part", super::random_base64_url(24)));
            match create_exclusive(&path, private) {
                Ok(file) => {
                    return Ok(Self {
                        path,
                        file: Some(file),
                    })
                }
                Err(error) if error.kind() == io::ErrorKind::AlreadyExists => continue,
                Err(error) => return Err(error),
            }
        }
        Err(io::Error::new(
            io::ErrorKind::AlreadyExists,
            "Could not allocate download staging.",
        ))
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    pub fn writer(&mut self) -> &mut File {
        self.file.as_mut().expect("staging file is still open")
    }

    pub fn close(&mut self) -> io::Result<()> {
        if let Some(mut file) = self.file.take() {
            file.flush()?;
        }
        Ok(())
    }
}

impl Drop for StagedFile {
    fn drop(&mut self) {
        // Close before unlinking: Windows does not generally permit deleting open files.
        drop(self.file.take());
        let _ = fs::remove_file(&self.path);
    }
}

fn create_exclusive(path: &Path, private: bool) -> io::Result<File> {
    #[cfg(windows)]
    if private {
        return windows_private::create(path);
    }
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    if private {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    options.open(path)
}

/// Status and length checks precede file creation. Any stream/length failure drops staging.
pub fn stage_download<R: Read>(
    reader: R,
    status: u16,
    declared: Option<u64>,
    target: &Path,
    limit: u64,
    private: bool,
) -> io::Result<StagedFile> {
    if !(200..300).contains(&status) {
        return Err(invalid(&format!("Download returned HTTP {status}.")));
    }
    check_declared_size(declared, limit)?;
    let mut staging = StagedFile::new(target, private)?;
    let copied = copy_bounded(reader, staging.writer(), limit)?;
    if copied == 0 {
        return Err(invalid("Downloaded file is empty."));
    }
    if declared.is_some_and(|size| size != copied) {
        return Err(invalid("Download size does not match its declared length."));
    }
    staging.close()?;
    Ok(staging)
}

pub fn java_argfile(arguments: &[String]) -> io::Result<Vec<u8>> {
    let mut encoded = Vec::new();
    for argument in arguments {
        #[cfg(windows)]
        let bytes = windows_private::encode_argfile(argument)?;
        #[cfg(not(windows))]
        let bytes = argument.as_bytes();
        quote_java_argument_bytes(&bytes, &mut encoded)?;
    }
    Ok(encoded)
}

fn quote_java_argument_bytes(argument: &[u8], encoded: &mut Vec<u8>) -> io::Result<()> {
    encoded.push(b'"');
    // JLI parses argument-file syntax as bytes before native-code-page decoding.
    // Escape even a DBCS trail byte 0x5c (e.g. CP932 表), not just Unicode '\\'.
    for byte in argument {
        match byte {
            0 => return Err(invalid("Java arguments cannot contain NUL.")),
            b'\\' => encoded.extend_from_slice(b"\\\\"),
            b'"' => encoded.extend_from_slice(b"\\\""),
            b'\n' => encoded.extend_from_slice(b"\\n"),
            b'\r' => encoded.extend_from_slice(b"\\r"),
            b'\t' => encoded.extend_from_slice(b"\\t"),
            0x0c => encoded.extend_from_slice(b"\\f"),
            byte => encoded.push(*byte),
        }
    }
    encoded.extend_from_slice(b"\"\n");
    Ok(())
}

/// Retain the private argument file until Java exits, including before it has opened it.
pub struct PrivateChild {
    child: Child,
    arguments: Option<JavaArguments>,
}

impl PrivateChild {
    pub fn spawn(mut command: Command, arguments: &[String], folder: &Path) -> io::Result<Self> {
        let encoded = java_argfile(arguments)?;
        let mut file = JavaArguments::new(folder, &encoded)?;
        let mut at_file = std::ffi::OsString::from("@");
        at_file.push(file.path());
        command.arg(at_file);
        let mut child = command.spawn()?;
        if let Err(error) = file.record_child(child.id()) {
            // Do not leave a successfully spawned child with an unrecorded lifetime
            // on an ordinary I/O failure. Abrupt parent termination is handled later.
            let _ = child.kill();
            if !confirm_exit(
                || child.try_wait().map(|status| status.is_some()),
                std::time::Duration::from_secs(3),
            ) {
                // No resident watcher: an unconfirmed live child must retain its
                // private argument file. An inconclusive record needs manual review.
                file.retain_for_recovery();
            }
            return Err(error);
        }
        Ok(Self {
            child,
            arguments: Some(file),
        })
    }

    pub fn id(&self) -> u32 {
        self.child.id()
    }

    pub fn kill(&mut self) -> io::Result<()> {
        self.child.kill()
    }

    pub fn wait(&mut self) -> io::Result<ExitStatus> {
        let status = self.child.wait()?;
        self.arguments.take();
        Ok(status)
    }

    pub fn try_wait(&mut self) -> io::Result<Option<ExitStatus>> {
        let status = self.child.try_wait()?;
        if status.is_some() {
            self.arguments.take();
        }
        Ok(status)
    }
}

fn confirm_exit(mut poll: impl FnMut() -> io::Result<bool>, timeout: std::time::Duration) -> bool {
    let deadline = std::time::Instant::now() + timeout;
    loop {
        if matches!(poll(), Ok(true)) {
            return true;
        }
        let Some(remaining) = deadline.checked_duration_since(std::time::Instant::now()) else {
            return false;
        };
        std::thread::sleep(remaining.min(std::time::Duration::from_millis(25)));
    }
}

/// The directory is only ever populated with generated extraction output.
pub struct StagedDirectory {
    path: PathBuf,
}

impl StagedDirectory {
    pub fn new(target: &Path) -> io::Result<Self> {
        let parent = target.parent().unwrap_or_else(|| Path::new("."));
        fs::create_dir_all(parent)?;
        for _ in 0..16 {
            let path = parent.join(format!(".gamble-extract-{}", super::random_base64_url(24)));
            let mut builder = fs::DirBuilder::new();
            #[cfg(unix)]
            {
                use std::os::unix::fs::DirBuilderExt;
                builder.mode(0o700);
            }
            match builder.create(&path) {
                Ok(()) => return Ok(Self { path }),
                Err(error) if error.kind() == io::ErrorKind::AlreadyExists => continue,
                Err(error) => return Err(error),
            }
        }
        Err(io::Error::new(
            io::ErrorKind::AlreadyExists,
            "Could not allocate extraction staging.",
        ))
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    pub fn install(&self, target: &Path) -> io::Result<()> {
        // Reserve a unique rollback path, never reuse or remove an unrelated backup.
        let backup = Self::new(target)?;
        fs::remove_dir(backup.path())?;
        let had_target = target.try_exists()?;
        if had_target {
            fs::rename(target, backup.path())?;
        }
        if let Err(error) = fs::rename(&self.path, target) {
            if had_target {
                // Keep the backup if restoring it fails.
                if let Err(restore_error) = fs::rename(backup.path(), target) {
                    let path = backup.path.clone();
                    std::mem::forget(backup);
                    return Err(io::Error::other(format!(
                        "Extraction install failed: {error}; restore failed: {restore_error}; previous files retained at {}",
                        path.display()
                    )));
                }
            }
            return Err(error);
        }
        Ok(())
    }
}

impl Drop for StagedDirectory {
    fn drop(&mut self) {
        let _ = fs::remove_dir_all(&self.path);
    }
}

#[cfg(windows)]
mod windows_private {
    use super::*;
    use std::{
        os::windows::{
            ffi::OsStrExt,
            io::{AsRawHandle, FromRawHandle, OwnedHandle},
        },
        ptr,
    };
    use windows_sys::Win32::{
        Foundation::{
            LocalFree, ERROR_INVALID_PARAMETER, FILETIME, GENERIC_WRITE, INVALID_HANDLE_VALUE,
            WAIT_OBJECT_0, WAIT_TIMEOUT,
        },
        Globalization::{
            GetACP, MultiByteToWideChar, WideCharToMultiByte, CP_UTF8, WC_ERR_INVALID_CHARS,
            WC_NO_BEST_FIT_CHARS,
        },
        Security::{
            Authorization::{
                ConvertSecurityDescriptorToStringSecurityDescriptorW, ConvertSidToStringSidW,
                ConvertStringSecurityDescriptorToSecurityDescriptorW, GetNamedSecurityInfoW,
                SE_FILE_OBJECT,
            },
            GetTokenInformation, TokenUser, DACL_SECURITY_INFORMATION, OWNER_SECURITY_INFORMATION,
            SECURITY_ATTRIBUTES, TOKEN_QUERY, TOKEN_USER,
        },
        Storage::FileSystem::{
            CreateDirectoryW, CreateFileW, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, FILE_SHARE_READ,
        },
        System::Threading::{
            GetCurrentProcess, GetProcessTimes, OpenProcess, OpenProcessToken, WaitForSingleObject,
            PROCESS_QUERY_LIMITED_INFORMATION, PROCESS_SYNCHRONIZE,
        },
    };

    pub(super) fn encode_argfile(text: &str) -> io::Result<Vec<u8>> {
        let wide: Vec<u16> = text.encode_utf16().collect();
        let length =
            i32::try_from(wide.len()).map_err(|_| invalid("Java argument file is too large."))?;
        if length == 0 {
            return Ok(Vec::new());
        }
        // JDK 21's Windows launcher decodes argument files using CP_ACP. Reject
        // lossy/best-fit conversion rather than silently changing paths or secrets.
        unsafe {
            let code_page = GetACP();
            let flags = if code_page == CP_UTF8 {
                WC_ERR_INVALID_CHARS
            } else {
                WC_NO_BEST_FIT_CHARS
            };
            let mut substituted = 0;
            let used_default = if code_page == CP_UTF8 {
                ptr::null_mut()
            } else {
                &mut substituted
            };
            let size = WideCharToMultiByte(
                code_page,
                flags,
                wide.as_ptr(),
                length,
                ptr::null_mut(),
                0,
                ptr::null(),
                used_default,
            );
            if size == 0 {
                return Err(io::Error::last_os_error());
            }
            if substituted != 0 {
                return Err(invalid(
                    "Java arguments are not representable in the Windows system code page.",
                ));
            }
            let mut bytes = vec![0u8; size as usize];
            let count = WideCharToMultiByte(
                code_page,
                flags,
                wide.as_ptr(),
                length,
                bytes.as_mut_ptr(),
                size,
                ptr::null(),
                used_default,
            );
            if count == 0 {
                return Err(io::Error::last_os_error());
            }
            if substituted != 0 || count != size {
                return Err(invalid(
                    "Java arguments are not representable in the Windows system code page.",
                ));
            }
            // Windows code pages can map different Unicode characters to the same
            // byte without flagging substitution (e.g. yen/backslash). Require an
            // exact decode-back match, not merely a successful conversion.
            let decoded_size =
                MultiByteToWideChar(code_page, 0, bytes.as_ptr(), count, ptr::null_mut(), 0);
            if decoded_size == 0 {
                return Err(io::Error::last_os_error());
            }
            let mut decoded = vec![0u16; decoded_size as usize];
            if MultiByteToWideChar(
                code_page,
                0,
                bytes.as_ptr(),
                count,
                decoded.as_mut_ptr(),
                decoded_size,
            ) != decoded_size
            {
                return Err(io::Error::last_os_error());
            }
            if decoded != wide {
                return Err(invalid(
                    "Java arguments do not round-trip through the Windows system code page.",
                ));
            }
            Ok(bytes)
        }
    }

    pub(super) fn current_user_sid() -> io::Result<String> {
        // SAFETY: token ownership is transferred to OwnedHandle; all native buffers are
        // correctly aligned and live until the SID is copied into an owned Rust string.
        unsafe {
            let mut token = ptr::null_mut();
            if OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &mut token) == 0 {
                return Err(io::Error::last_os_error());
            }
            let token = OwnedHandle::from_raw_handle(token);
            let mut length = 0;
            GetTokenInformation(
                token.as_raw_handle(),
                TokenUser,
                ptr::null_mut(),
                0,
                &mut length,
            );
            if length < std::mem::size_of::<TOKEN_USER>() as u32 {
                return Err(io::Error::last_os_error());
            }
            let mut buffer = vec![0usize; (length as usize).div_ceil(std::mem::size_of::<usize>())];
            if GetTokenInformation(
                token.as_raw_handle(),
                TokenUser,
                buffer.as_mut_ptr().cast(),
                length,
                &mut length,
            ) == 0
            {
                return Err(io::Error::last_os_error());
            }
            let user = &*buffer.as_ptr().cast::<TOKEN_USER>();
            let mut text = ptr::null_mut();
            if ConvertSidToStringSidW(user.User.Sid, &mut text) == 0 {
                return Err(io::Error::last_os_error());
            }
            let mut count = 0;
            while *text.add(count) != 0 {
                count += 1;
            }
            let sid = String::from_utf16_lossy(std::slice::from_raw_parts(text, count));
            LocalFree(text.cast());
            Ok(sid)
        }
    }

    fn with_private_attributes<T>(
        path: &Path,
        operation: impl FnOnce(*const u16, &SECURITY_ATTRIBUTES) -> io::Result<T>,
    ) -> io::Result<T> {
        // Bind owner and protected DACL to the current process token's user SID. No
        // inherited ACEs and no environment-derived username/account lookup.
        let sid = current_user_sid()?;
        let sddl: Vec<u16> = format!("O:{sid}D:P(A;;FA;;;{sid})\0")
            .encode_utf16()
            .collect();
        let mut descriptor = ptr::null_mut();
        let mut name: Vec<u16> = path.as_os_str().encode_wide().collect();
        if name.contains(&0) {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "File path contains NUL.",
            ));
        }
        name.push(0);
        // SAFETY: NUL-terminated inputs, valid out pointer; descriptor is LocalFree-owned.
        unsafe {
            if ConvertStringSecurityDescriptorToSecurityDescriptorW(
                sddl.as_ptr(),
                1,
                &mut descriptor,
                ptr::null_mut(),
            ) == 0
            {
                return Err(io::Error::last_os_error());
            }
            let attributes = SECURITY_ATTRIBUTES {
                nLength: std::mem::size_of::<SECURITY_ATTRIBUTES>() as u32,
                lpSecurityDescriptor: descriptor,
                bInheritHandle: 0,
            };
            let result = operation(name.as_ptr(), &attributes);
            LocalFree(descriptor);
            result
        }
    }

    pub fn create(path: &Path) -> io::Result<File> {
        with_private_attributes(path, |name, attributes| unsafe {
            let handle = CreateFileW(
                name,
                GENERIC_WRITE,
                FILE_SHARE_READ,
                attributes,
                CREATE_NEW,
                FILE_ATTRIBUTE_NORMAL,
                ptr::null_mut(),
            );
            if handle == INVALID_HANDLE_VALUE {
                Err(io::Error::last_os_error())
            } else {
                Ok(File::from_raw_handle(handle))
            }
        })
    }

    pub(super) fn create_directory(path: &Path) -> io::Result<()> {
        with_private_attributes(path, |name, attributes| unsafe {
            if CreateDirectoryW(name, attributes) == 0 {
                Err(io::Error::last_os_error())
            } else {
                Ok(())
            }
        })
    }

    pub(super) fn has_private_acl(path: &Path) -> io::Result<bool> {
        let sid = current_user_sid()?;
        // Windows prints well-known account SIDs using aliases (for example LA
        // for this machine's RID-500 user). Canonicalize the exact expected
        // descriptor too; never broaden the permitted owner or ACE list.
        let expected = canonical_sddl(&format!("O:{sid}D:P(A;;FA;;;{sid})"))?;
        let name: Vec<u16> = path.as_os_str().encode_wide().chain(Some(0)).collect();
        unsafe {
            let mut descriptor = ptr::null_mut();
            let info = DACL_SECURITY_INFORMATION | OWNER_SECURITY_INFORMATION;
            let error = GetNamedSecurityInfoW(
                name.as_ptr(),
                SE_FILE_OBJECT,
                info,
                ptr::null_mut(),
                ptr::null_mut(),
                ptr::null_mut(),
                ptr::null_mut(),
                &mut descriptor,
            );
            if error != 0 {
                return Err(io::Error::from_raw_os_error(error as i32));
            }
            let mut text = ptr::null_mut();
            if ConvertSecurityDescriptorToStringSecurityDescriptorW(
                descriptor,
                1,
                info,
                &mut text,
                ptr::null_mut(),
            ) == 0
            {
                let error = io::Error::last_os_error();
                LocalFree(descriptor);
                return Err(error);
            }
            let mut count = 0;
            while *text.add(count) != 0 {
                count += 1;
            }
            let sddl = String::from_utf16_lossy(std::slice::from_raw_parts(text, count));
            LocalFree(text.cast());
            LocalFree(descriptor);
            Ok(sddl == expected)
        }
    }

    pub(super) fn canonical_sddl(sddl: &str) -> io::Result<String> {
        let wide: Vec<u16> = sddl.encode_utf16().chain(Some(0)).collect();
        if sddl.contains('\0') {
            return Err(invalid("Security descriptor contains NUL."));
        }
        // Both conversions allocate LocalFree-owned buffers. Only the owner
        // and DACL are compared, matching the fields read from the real file.
        unsafe {
            let mut descriptor = ptr::null_mut();
            if ConvertStringSecurityDescriptorToSecurityDescriptorW(
                wide.as_ptr(),
                1,
                &mut descriptor,
                ptr::null_mut(),
            ) == 0
            {
                return Err(io::Error::last_os_error());
            }
            let mut text = ptr::null_mut();
            if ConvertSecurityDescriptorToStringSecurityDescriptorW(
                descriptor,
                1,
                DACL_SECURITY_INFORMATION | OWNER_SECURITY_INFORMATION,
                &mut text,
                ptr::null_mut(),
            ) == 0
            {
                let error = io::Error::last_os_error();
                LocalFree(descriptor);
                return Err(error);
            }
            let mut count = 0;
            while *text.add(count) != 0 {
                count += 1;
            }
            let canonical = String::from_utf16_lossy(std::slice::from_raw_parts(text, count));
            LocalFree(text.cast());
            LocalFree(descriptor);
            Ok(canonical)
        }
    }

    pub(super) fn process_identity(pid: u32) -> io::Result<Option<String>> {
        if pid == 0 {
            return Err(invalid("Unknown Java process identity."));
        }
        unsafe {
            let handle = OpenProcess(
                PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_SYNCHRONIZE,
                0,
                pid,
            );
            if handle.is_null() {
                let error = io::Error::last_os_error();
                return if error.raw_os_error() == Some(ERROR_INVALID_PARAMETER as i32) {
                    Ok(None)
                } else {
                    Err(error)
                };
            }
            let handle = OwnedHandle::from_raw_handle(handle);
            // A terminated Windows process object can remain open while another
            // process holds a handle. Its creation time still exists, so query
            // the signaled state before treating it as a live/reused PID. Do not
            // infer liveness from exit code 259, which can be a real exit code.
            match WaitForSingleObject(handle.as_raw_handle(), 0) {
                WAIT_OBJECT_0 => return Ok(None),
                WAIT_TIMEOUT => {}
                _ => return Err(io::Error::last_os_error()),
            }
            let mut created: FILETIME = std::mem::zeroed();
            let mut exited: FILETIME = std::mem::zeroed();
            let mut kernel: FILETIME = std::mem::zeroed();
            let mut user: FILETIME = std::mem::zeroed();
            if GetProcessTimes(
                handle.as_raw_handle(),
                &mut created,
                &mut exited,
                &mut kernel,
                &mut user,
            ) == 0
            {
                return Err(io::Error::last_os_error());
            }
            let ticks = ((created.dwHighDateTime as u64) << 32) | created.dwLowDateTime as u64;
            Ok(Some(format!("windows:{ticks}")))
        }
    }
}

#[cfg(test)]
#[path = "io_safety_tests.rs"]
mod tests;
