use super::*;
use std::{
    io::Cursor,
    sync::{atomic::AtomicBool, Arc},
    thread,
};
use zip::{write::SimpleFileOptions, CompressionMethod, ZipWriter};

fn folder() -> StagedDirectory {
    StagedDirectory::new(&std::env::temp_dir().join("gamble-security-test-target")).unwrap()
}

#[test]
fn command_log_redacts_short_and_leading_dash_token_values() {
    let args = [
        "java",
        "--accessToken",
        "tiny",
        "--access-token",
        "-sentinel",
        "--clientToken",
        "short-client",
        "--username",
        "Player",
    ]
    .map(str::to_string);
    let log = redacted_command(&args);
    for secret in ["tiny", "-sentinel", "short-client"] {
        assert!(!log.contains(secret));
    }
    assert!(log.contains("--username Player"));
    assert_eq!(log.matches("<redacted>").count(), 3);
    assert_eq!(redacted_command(&["x".repeat(61)]), "<token-or-path>");
}

fn archive(entries: &[(&str, &[u8])]) -> Vec<u8> {
    let mut zip = ZipWriter::new(Cursor::new(Vec::new()));
    for (name, bytes) in entries {
        zip.start_file(
            *name,
            SimpleFileOptions::default().compression_method(CompressionMethod::Deflated),
        )
        .unwrap();
        zip.write_all(bytes).unwrap();
    }
    zip.finish().unwrap().into_inner()
}

fn fake_uncompressed_size(mut zip: Vec<u8>, size: u32) -> Vec<u8> {
    for index in 0..zip.len().saturating_sub(28) {
        let offset = if zip[index..].starts_with(b"PK\x03\x04") {
            22
        } else if zip[index..].starts_with(b"PK\x01\x02") {
            24
        } else {
            continue;
        };
        zip[index + offset..index + offset + 4].copy_from_slice(&size.to_le_bytes());
    }
    zip
}

#[test]
fn runtime_extraction_limits_actual_inflated_bytes_and_preserves_prior_install() {
    let root = folder();
    let target = root.path().join("runtime");
    fs::create_dir(&target).unwrap();
    fs::write(target.join("old"), b"previous").unwrap();
    let payload = vec![b'x'; 1_000_000];
    let zip = fake_uncompressed_size(archive(&[("jdk/bin/java.exe", &payload)]), 1);
    let error = extract_runtime_bounded(Cursor::new(zip), &target, 16, 128).unwrap_err();
    assert!(error.contains("safety limit"), "{error}");
    assert_eq!(fs::read(target.join("old")).unwrap(), b"previous");
    assert!(!target.join("jdk").exists());
    assert_eq!(fs::read_dir(root.path()).unwrap().count(), 1);
}

#[test]
fn archive_metadata_is_bounded_even_with_false_entry_size() {
    let payload = vec![b'x'; 100_000];
    let bytes = fake_uncompressed_size(archive(&[("fabric.mod.json", &payload)]), 1);
    let mut zip = ZipArchive::new(Cursor::new(bytes)).unwrap();
    let mut entry = zip.by_index(0).unwrap();
    let error = read_zip_metadata(&mut entry, 128).unwrap_err();
    assert!(error.contains("safety limit"), "{error}");
}

#[test]
fn archive_limits_entry_count_paths_sizes_and_cleans_failed_staging() {
    for (entries, max_files, limit) in [
        (vec![("one", b"a".as_slice()), ("two", b"b")], 1, 16),
        (vec![("../escape", b"bad".as_slice())], 16, 16),
        (vec![("/absolute", b"bad".as_slice())], 16, 16),
        (vec![("one", b"12345".as_slice()), ("two", b"67890")], 16, 8),
    ] {
        let root = folder();
        let target = root.path().join("runtime");
        assert!(
            extract_runtime_bounded(Cursor::new(archive(&entries)), &target, max_files, limit)
                .is_err()
        );
        assert!(!target.exists());
        assert_eq!(fs::read_dir(root.path()).unwrap().count(), 0);
    }
}

#[test]
fn valid_runtime_extraction_replaces_previous_tree_with_complete_tree() {
    let root = folder();
    let target = root.path().join("runtime");
    fs::create_dir(&target).unwrap();
    fs::write(target.join("old"), b"old").unwrap();
    let bytes = archive(&[("jdk/bin/java.exe", b"12345"), ("jdk/release", b"67890")]);
    extract_runtime_bounded(Cursor::new(bytes), &target, 2, 10).unwrap();
    assert!(!target.join("old").exists());
    assert_eq!(fs::read(target.join("jdk/bin/java.exe")).unwrap(), b"12345");
    assert_eq!(fs::read_dir(root.path()).unwrap().count(), 1);
}

#[test]
fn native_extraction_flattens_and_shares_actual_expansion_budget() {
    let root = folder();
    let mut expanded = 0;
    let bytes = archive(&[
        ("META-INF/skip", &[b'x'; 100]),
        ("nested/native.dll", b"12345"),
    ]);
    unpack_zip_bounded(Cursor::new(bytes), root.path(), true, 16, 8, &mut expanded).unwrap();
    assert_eq!(expanded, 5);
    assert_eq!(fs::read(root.path().join("native.dll")).unwrap(), b"12345");
    assert!(!root.path().join("META-INF").exists());
    let bytes = archive(&[("another.dll", b"67890")]);
    assert!(
        unpack_zip_bounded(Cursor::new(bytes), root.path(), true, 16, 8, &mut expanded).is_err()
    );
    assert_eq!(expanded, 5);
}

#[test]
fn callback_accepts_fragmented_headers_and_decodes_query() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap();
    let worker = thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        read_callback_with_deadline(&mut stream, || false, Duration::from_secs(2))
    });
    let mut client = TcpStream::connect(address).unwrap();
    for part in [
        "GET /?co",
        "de=fake%20code&state=state HTTP/1.1\r\nHo",
        "st: localhost\r\n",
        "\r\n",
    ] {
        client.write_all(part.as_bytes()).unwrap();
        thread::sleep(Duration::from_millis(10));
    }
    let query = worker.join().unwrap().unwrap();
    assert_eq!(query.get("code").unwrap(), "fake code");
    assert_eq!(query.get("state").unwrap(), "state");
}

#[test]
fn silent_callback_cancellation_releases_listener_for_rebind() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap();
    let cancelled = Arc::new(AtomicBool::new(false));
    let read_cancelled = cancelled.clone();
    let (ready_tx, ready_rx) = std::sync::mpsc::channel();
    let worker = thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        ready_tx.send(()).unwrap();
        read_callback_with_deadline(
            &mut stream,
            || read_cancelled.load(Ordering::SeqCst),
            Duration::from_secs(5),
        )
    });
    let client = TcpStream::connect(address).unwrap();
    ready_rx.recv_timeout(Duration::from_secs(2)).unwrap();
    let started = Instant::now();
    cancelled.store(true, Ordering::SeqCst);
    assert!(worker.join().unwrap().unwrap_err().contains("cancelled"));
    assert!(started.elapsed() < Duration::from_secs(1));
    drop(client);
    TcpListener::bind(address).unwrap();
}

#[test]
fn silent_callback_times_out_and_releases_port() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let address = listener.local_addr().unwrap();
    let worker = thread::spawn(move || {
        let (mut stream, _) = listener.accept().unwrap();
        read_callback_with_deadline(&mut stream, || false, Duration::from_millis(80))
    });
    let client = TcpStream::connect(address).unwrap();
    let started = Instant::now();
    assert!(worker.join().unwrap().unwrap_err().contains("timed out"));
    assert!(started.elapsed() < Duration::from_secs(1));
    drop(client);
    TcpListener::bind(address).unwrap();
}

#[test]
fn callback_rejects_oversized_incomplete_and_invalid_requests() {
    for request in [
        vec![b'x'; 8192],
        b"GET / HTTP/1.1\r\n".to_vec(),
        b"POST / HTTP/1.1\r\n\r\n".to_vec(),
    ] {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let worker = thread::spawn(move || {
            let (mut stream, _) = listener.accept().unwrap();
            read_callback_with_deadline(&mut stream, || false, Duration::from_secs(2))
        });
        let mut client = TcpStream::connect(address).unwrap();
        client.write_all(&request).unwrap();
        client.shutdown(std::net::Shutdown::Write).unwrap();
        assert!(worker.join().unwrap().is_err());
    }
}
