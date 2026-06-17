use mdns_sd::{ServiceDaemon, ServiceInfo};
use std::collections::HashMap;

/// 持有 mDNS daemon;drop 时注销服务。
pub struct MdnsHandle {
    daemon: ServiceDaemon,
    fullname: String,
}

impl Drop for MdnsHandle {
    fn drop(&mut self) {
        let _ = self.daemon.unregister(&self.fullname);
    }
}

/// 广播 `_tingmusic._tcp` 服务,TXT 带 v/port/lib。
pub fn advertise(port: u16, library_name: &str) -> anyhow::Result<MdnsHandle> {
    let daemon = ServiceDaemon::new()?;
    let ty_domain = "_tingmusic._tcp.local.";
    let instance = "TingMusic";
    let host = format!("tingmusic-{port}.local.");

    let mut props: HashMap<String, String> = HashMap::new();
    props.insert("v".into(), "1".into());
    props.insert("port".into(), port.to_string());
    props.insert("lib".into(), library_name.to_string());

    // 空 IP + enable_addr_auto:让 mdns-sd 自动用本机所有网卡地址。
    let service = ServiceInfo::new(ty_domain, instance, &host, "", port, props)?
        .enable_addr_auto();
    let fullname = service.get_fullname().to_string();
    daemon.register(service)?;
    Ok(MdnsHandle { daemon, fullname })
}
