pub mod client;
pub mod editions;
pub mod home;
pub mod models;
pub mod parser;
pub mod schedule;

pub use client::*;
pub use editions::*;
pub use home::*;
pub use models::*;
pub use parser::*;
pub use schedule::*;

pub use crate::api::search::search_anime;
