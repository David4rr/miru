use miru_core::api::providers;
use miru_core::db::{Database, WatchHistoryItem};

#[tokio::test]
async fn test_provider_registry_and_servers() {
    let hianime = providers::get_provider("hianime").expect("HiAnime should exist");
    assert_eq!(hianime.id(), "hianime");
    assert!(hianime.has_sub());
    assert!(hianime.has_dub());

    let servers_sub = hianime.get_servers("one-piece", 1, "sub").await.unwrap();
    assert!(!servers_sub.is_empty());
    assert!(servers_sub.iter().any(|s| s.server_id == "megacloud"));

    let servers_dub = hianime.get_servers("one-piece", 1, "dub").await.unwrap();
    assert!(!servers_dub.is_empty());

    let anichan = providers::get_provider("anichan").expect("AniChan should exist");
    assert_eq!(anichan.id(), "anichan");
    assert!(anichan.has_sub());
    assert!(!anichan.has_dub());
    assert!(anichan.get_servers("naruto", 1, "dub").await.is_err());
    let anichan_servers = anichan.get_servers("naruto", 1, "sub").await.unwrap();
    assert!(anichan_servers.iter().any(|s| s.server_id == "pixeldrain"));

    let otakudesu = providers::get_provider("otakudesu").expect("Otakudesu should exist");
    assert_eq!(otakudesu.id(), "otakudesu");
    assert!(otakudesu.has_sub());
    assert!(!otakudesu.has_dub());
    let otaku_servers = otakudesu.get_servers("bleach", 1, "sub").await.unwrap();
    assert!(otaku_servers.iter().any(|s| s.server_id == "desustream"));

    assert!(providers::get_provider("unknown_xyz").is_none());
}

#[test]
fn test_default_provider_persistence() {
    let path = std::env::temp_dir().join(format!("miru_test_prov_{}.db", std::process::id()));
    let _ = std::fs::remove_file(&path);
    let db = Database::init(&path).unwrap();

    assert_eq!(providers::get_default_provider_id(&db), "hianime");

    providers::set_default_provider_id(&db, "anichan").unwrap();
    assert_eq!(providers::get_default_provider_id(&db), "anichan");

    assert!(providers::set_default_provider_id(&db, "fake_prov").is_err());
    assert_eq!(providers::get_default_provider_id(&db), "anichan");

    let _ = std::fs::remove_file(&path);
}

#[test]
fn test_sub_and_dub_mode_isolation_in_db() {
    let path = std::env::temp_dir().join(format!("miru_test_iso_{}.db", std::process::id()));
    let _ = std::fs::remove_file(&path);
    let db = Database::init(&path).unwrap();

    // 1. Add SUB history
    db.add_history(&WatchHistoryItem {
        id: Some(1),
        slug: "frieren".to_string(),
        title: "Frieren".to_string(),
        episode: 5,
        mode: "sub".to_string(),
        progress: 45,
        poster: "frieren.jpg".to_string(),
        anime_type: "TV".to_string(),
        played_at: None,
    }).unwrap();

    // 2. Add DUB history for same anime
    db.add_history(&WatchHistoryItem {
        id: Some(2),
        slug: "frieren".to_string(),
        title: "Frieren".to_string(),
        episode: 1,
        mode: "dub".to_string(),
        progress: 10,
        poster: "frieren.jpg".to_string(),
        anime_type: "TV".to_string(),
        played_at: None,
    }).unwrap();

    let history = db.get_history(10).unwrap();
    assert_eq!(history.len(), 2, "SUB and DUB must coexist in watch history");

    let sub_item = history.iter().find(|h| h.mode == "sub").unwrap();
    assert_eq!(sub_item.episode, 5);
    assert_eq!(sub_item.progress, 45);

    let dub_item = history.iter().find(|h| h.mode == "dub").unwrap();
    assert_eq!(dub_item.episode, 1);
    assert_eq!(dub_item.progress, 10);

    // 3. Test watch_progress isolation
    db.save_progress("frieren", 5, Some("sub"), 80, 1400, None, None, None).unwrap();
    db.save_progress("frieren", 5, Some("dub"), 20, 1400, None, None, None).unwrap();

    let p_sub = db.get_progress("frieren", 5, Some("sub")).unwrap().unwrap();
    assert_eq!(p_sub.progress, 80);

    let p_dub = db.get_progress("frieren", 5, Some("dub")).unwrap().unwrap();
    assert_eq!(p_dub.progress, 20);

    let _ = std::fs::remove_file(&path);
}
