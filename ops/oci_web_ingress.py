import argparse
from ipaddress import IPv4Address
import json
from pathlib import Path


def web_ingress(snapshot, admin_ip):
    address = IPv4Address(admin_ip)
    if not address.is_global or address.is_multicast:
        raise ValueError("SSH 관리 주소에는 인터넷에서 연결 가능한 IPv4 한 개가 필요합니다.")

    rules = []
    for current in snapshot["data"]["ingress-security-rules"]:
        protocol = current["protocol"]
        if protocol == "1":
            rules.append({
                "protocol": protocol,
                "source": current["source"],
                "sourceType": current["source-type"],
                "isStateless": current["is-stateless"],
                "icmpOptions": current["icmp-options"],
            })
            continue

        destination = (current.get("tcp-options") or {}).get("destination-port-range") or {}
        if protocol != "6" or destination.get("min") not in (22, 80, 443) or destination.get("min") != destination.get("max"):
            raise ValueError("확인하지 않은 기존 수신 규칙이 있어 변경을 중단했습니다.")

    web_ports = {22: f"{address}/32", 80: "0.0.0.0/0", 443: "0.0.0.0/0"}
    for port, source in web_ports.items():
        rules.append({
            "protocol": "6",
            "source": source,
            "sourceType": "CIDR_BLOCK",
            "isStateless": False,
            "tcpOptions": {"destinationPortRange": {"min": port, "max": port}},
        })
    return rules


def main():
    parser = argparse.ArgumentParser(description="Generate ingress rules for the dedicated OCI demo web subnet.")
    parser.add_argument("security_list", type=Path)
    parser.add_argument("admin_ip")
    args = parser.parse_args()
    snapshot = json.loads(args.security_list.read_text(encoding="utf-8-sig"))
    try:
        result = web_ingress(snapshot, args.admin_ip)
    except ValueError as error:
        parser.error(str(error))
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
