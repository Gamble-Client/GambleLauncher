use super::*;

fn folder() -> StagedDirectory {
    StagedDirectory::new(&std::env::temp_dir().join("gamble-argfile-recovery-test")).unwrap()
}

#[test]
fn unconfirmed_child_failure_keeps_private_files_and_unknown_record() {
    let root = folder();
    let args = JavaArguments::new(root.path(), b"\"fake-bearer\"\n").unwrap();
    let path = args.path().to_owned();
    args.retain_for_recovery();
    assert!(path.exists());
    assert!(path.with_extension("child").exists());
    assert_eq!(
        cleanup_with(root.path(), |_| ProcessState::Gone).unwrap(),
        0
    );
}

#[test]
fn orphan_scan_has_a_512_entry_enumeration_budget() {
    let root = folder();
    let directory = ensure_directory(root.path()).unwrap();
    let identity = current_identity();
    for index in 0..513 {
        let nonce = format!("{index:032}");
        let path = directory.join(format!("{PREFIX}{nonce}.args"));
        let mut file = create_exclusive(&path, true).unwrap();
        writeln!(
            file,
            "{MARKER}{}",
            serde_json::to_string(&Header {
                nonce: nonce.clone()
            })
            .unwrap()
        )
        .unwrap();
        drop(file);
        let record = create_exclusive(&path.with_extension("child"), true).unwrap();
        serde_json::to_writer(
            record,
            &ChildRecord {
                marker: RECORD_MARKER.into(),
                nonce,
                child: ProcessRef {
                    pid: 123,
                    identity: Some(identity.clone()),
                },
            },
        )
        .unwrap();
    }
    let probes = std::cell::Cell::new(0);
    cleanup_with(root.path(), |_| {
        probes.set(probes.get() + 1);
        ProcessState::Gone
    })
    .unwrap();
    assert!(probes.get() <= 512);
    assert!(fs::read_dir(directory).unwrap().any(|entry| entry
        .unwrap()
        .path()
        .extension()
        .is_some_and(|extension| extension == "args")));
}

fn current_identity() -> String {
    match process_state(std::process::id()) {
        ProcessState::Running(identity) => identity,
        state => panic!("supported platform must identify current process: {state:?}"),
    }
}

fn fixture(root: &Path, identity: Option<String>) -> JavaArguments {
    let args = JavaArguments::new(root, b"\"fake-short-bearer\"\n").unwrap();
    let data = ChildRecord {
        marker: RECORD_MARKER.into(),
        nonce: args.nonce.clone(),
        child: ProcessRef { pid: 123, identity },
    };
    super::super::super::write_private_file(
        args.record.as_ref().unwrap().path(),
        &serde_json::to_vec(&data).unwrap(),
    )
    .unwrap();
    args
}

#[test]
fn cleanup_deletes_only_positively_dead_marked_argument_and_sidecar() {
    let root = folder();
    let args = fixture(root.path(), Some(current_identity()));
    let path = args.path().to_owned();
    let record = path.with_extension("child");
    let unrelated = path.parent().unwrap().join("unrelated.part");
    fs::write(&unrelated, b"unowned data").unwrap();
    assert_eq!(
        cleanup_with(root.path(), |_| ProcessState::Gone).unwrap(),
        1
    );
    assert!(!path.exists());
    assert!(!record.exists());
    assert_eq!(fs::read(unrelated).unwrap(), b"unowned data");
}

#[test]
fn cleanup_retains_live_reused_unknown_or_missing_start_identity() {
    let root = folder();
    let identity = current_identity();
    let args = fixture(root.path(), Some(identity.clone()));
    for state in [
        ProcessState::Running(identity),
        ProcessState::Running("reused-pid-new-start".into()),
        ProcessState::Unknown,
    ] {
        assert!(!definitely_finished(
            &ProcessRef {
                pid: 123,
                identity: Some(current_identity())
            },
            state
        ));
    }
    assert_eq!(
        cleanup_with(root.path(), |_| ProcessState::Unknown).unwrap(),
        0
    );
    assert!(args.path().exists());
    drop(args);
    for identity in [None, Some("malformed-start-identity".into())] {
        let args = fixture(root.path(), identity);
        assert_eq!(
            cleanup_with(root.path(), |_| ProcessState::Gone).unwrap(),
            0
        );
        assert!(args.path().exists());
    }
}

#[test]
fn pending_missing_malformed_and_wrong_nonce_records_are_retained_without_age_guessing() {
    let root = folder();
    let args = JavaArguments::new(root.path(), b"\"sentinel\"\n").unwrap();
    assert_eq!(
        cleanup_with(root.path(), |_| ProcessState::Gone).unwrap(),
        0
    );
    let record = args.record.as_ref().unwrap().path();
    for bytes in [
        b"{partial-json".as_slice(),
        br#"{"marker":"other","nonce":"wrong","child":{"pid":123,"identity":"unknown"}}"#,
    ] {
        super::super::super::write_private_file(record, bytes).unwrap();
        assert_eq!(
            cleanup_with(root.path(), |_| ProcessState::Gone).unwrap(),
            0
        );
        assert!(args.path().exists());
    }
    fs::remove_file(record).unwrap();
    assert_eq!(
        cleanup_with(root.path(), |_| ProcessState::Gone).unwrap(),
        0
    );
    assert!(args.path().exists());
}

#[test]
fn unmarked_and_foreign_named_files_are_not_cleanup_targets() {
    let root = folder();
    let args = fixture(root.path(), Some(current_identity()));
    let foreign = args.path().parent().unwrap().join("java-sibling.args");
    fs::copy(args.path(), &foreign).unwrap();
    super::super::super::write_private_file(args.path(), b"not an argument marker\n").unwrap();
    assert_eq!(
        cleanup_with(root.path(), |_| ProcessState::Gone).unwrap(),
        0
    );
    assert!(args.path().exists());
    assert!(foreign.exists());
}

#[cfg(unix)]
#[test]
fn cleanup_refuses_symlinked_hardlinked_and_nonprivate_records_or_directories() {
    use std::os::unix::fs::{symlink, PermissionsExt};
    let root = folder();
    let args = fixture(root.path(), Some(current_identity()));
    let directory = args.path().parent().unwrap();
    let record = args.record.as_ref().unwrap().path();
    let moved = directory.join("other-data");
    fs::rename(record, &moved).unwrap();
    symlink(&moved, record).unwrap();
    assert_eq!(
        cleanup_with(root.path(), |_| ProcessState::Gone).unwrap(),
        0
    );
    fs::remove_file(record).unwrap();
    fs::rename(&moved, record).unwrap();
    fs::hard_link(args.path(), &moved).unwrap();
    assert_eq!(
        cleanup_with(root.path(), |_| ProcessState::Gone).unwrap(),
        0
    );
    fs::remove_file(&moved).unwrap();
    fs::set_permissions(args.path(), fs::Permissions::from_mode(0o644)).unwrap();
    assert_eq!(
        cleanup_with(root.path(), |_| ProcessState::Gone).unwrap(),
        0
    );
    fs::set_permissions(args.path(), fs::Permissions::from_mode(0o600)).unwrap();
    fs::set_permissions(directory, fs::Permissions::from_mode(0o755)).unwrap();
    assert_eq!(
        cleanup_with(root.path(), |_| ProcessState::Gone).unwrap(),
        0
    );
    assert!(JavaArguments::new(root.path(), b"new secret").is_err());
    fs::set_permissions(directory, fs::Permissions::from_mode(0o700)).unwrap();
    let original = root.path().join("original-dir");
    fs::rename(directory, &original).unwrap();
    symlink(&original, directory).unwrap();
    assert_eq!(
        cleanup_with(root.path(), |_| ProcessState::Gone).unwrap(),
        0
    );
    assert!(original.join(args.path().file_name().unwrap()).exists());
    // Restore fixture before its owned RAII paths are dropped.
    fs::remove_file(directory).unwrap();
    fs::rename(&original, directory).unwrap();
}

#[test]
fn proc_start_parser_handles_spaces_parentheses_and_reused_pid() {
    let fields = (3..=52)
        .map(|field| {
            if field == 22 {
                "123456".into()
            } else {
                field.to_string()
            }
        })
        .collect::<Vec<String>>()
        .join(" ");
    assert_eq!(
        linux_stat_identity(&format!("123 (java (odd) name)) {fields}"), "boot-a"),
        Some("linux:boot-a:123456".into())
    );
    assert_ne!(
        linux_stat_identity(&format!("123 (java) {fields}"), "boot-a"),
        linux_stat_identity(&format!("123 (java) {fields}"), "boot-b")
    );
    assert!(linux_stat_identity("partial", "boot").is_none());
}

#[cfg(any(target_os = "linux", windows))]
#[test]
fn real_child_survives_parent_owner_drop_then_next_cleanup_removes_only_after_exit() {
    use std::{
        io::{BufRead, BufReader},
        process::Stdio,
    };
    let root = folder();
    let source = root.path().join("WaitingJava.java");
    fs::write(&source, b"class WaitingJava { public static void main(String[] args) throws Exception { System.out.println(\"ready\"); System.out.flush(); System.in.read(); } }").unwrap();
    let mut command = Command::new("java");
    command
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::inherit());
    let mut child = PrivateChild::spawn(
        command,
        &[source.to_string_lossy().into_owned(), "fake-bearer".into()],
        root.path(),
    )
    .unwrap();
    let path = child.arguments.as_ref().unwrap().path().to_owned();
    let mut ready = String::new();
    BufReader::new(child.child.stdout.take().unwrap())
        .read_line(&mut ready)
        .unwrap();
    assert_eq!(ready.trim(), "ready");
    let mut arguments = child.arguments.take().unwrap();
    // Simulate abrupt parent termination: keep files but release Rust ownership,
    // retaining only the OS child in this fixture so it can be reaped reliably.
    arguments.file.close().unwrap();
    std::mem::forget(arguments);
    assert_eq!(cleanup_argument_files(root.path()).unwrap(), 0);
    assert!(path.exists());
    child.child.stdin.take().unwrap().write_all(b"\n").unwrap();
    assert!(child.wait().unwrap().success());
    assert_eq!(cleanup_argument_files(root.path()).unwrap(), 1);
    assert!(!path.exists());
    assert!(!path.with_extension("child").exists());
}
