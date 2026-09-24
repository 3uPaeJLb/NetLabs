import java.io.IOException;
import java.net.*;
import java.util.Enumeration;


public class NetworkUtils {

    private NetworkUtils() {}

    public static NetworkInterface pickMulticastInterface() throws IOException {
        System.out.println("[NET] Ищу multicast-интерфейс...");


        NetworkInterface radmin = pickRadmin();
        if (radmin != null) {
            System.out.println("[NET] Выбран (Radmin VPN, ЖЁСТКО): "
                    + radmin.getDisplayName());
            return radmin;
        }

        NetworkInterface ni = pickByInternetRoute();
        if (ni != null) {
            System.out.println("[NET] Выбран (по маршруту в интернет): "
                    + ni.getDisplayName());
            return ni;
        }

        ni = pickRealIpv4();
        if (ni != null) {
            System.out.println("[NET] Выбран (первый реальный IPv4): "
                    + ni.getDisplayName());
            return ni;
        }

        ni = pickAnyUpMulticastIpv4();
        if (ni != null) {
            System.out.println("[NET] Выбран (аварийный): "
                    + ni.getDisplayName());
            return ni;
        }

        System.out.println("[NET] FALLBACK -> loopback");
        return NetworkInterface.getByInetAddress(InetAddress.getLoopbackAddress());
    }


    private static NetworkInterface pickRadmin() throws SocketException {
        Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
        while (nis.hasMoreElements()) {
            NetworkInterface ni = nis.nextElement();

            String name = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();
            if (!name.contains("radmin")) continue;

            if (!ni.isUp() || !ni.supportsMulticast() || ni.isLoopback()) {
                System.out.println("[NET] Radmin найден, но не подходит: "
                        + ni.getDisplayName()
                        + " (up=" + ni.isUp()
                        + ", multicast=" + ni.supportsMulticast() + ")");
                continue;
            }

            if (!hasRealIpv4(ni)) {
                System.out.println("[NET] Radmin найден: "
                        + ni.getDisplayName());
                continue;
            }

            return ni;
        }
        return null;
    }

    private static NetworkInterface pickByInternetRoute() {
        String[] probes = { "8.8.8.8", "1.1.1.1", "208.67.222.222" };

        for (String probe : probes) {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.connect(InetAddress.getByName(probe), 53);
                InetAddress local = socket.getLocalAddress();

                if (local.isAnyLocalAddress() || local.isLoopbackAddress()) continue;

                NetworkInterface ni = NetworkInterface.getByInetAddress(local);
                if (ni == null) continue;

                if (isVpnOrVirtual(ni)) {
                    System.out.println("[NET] Маршрут через VPN/виртуальный ("
                            + ni.getDisplayName() + ") — пропускаю");
                    continue;
                }

                if (ni.isUp() && ni.supportsMulticast() && hasRealIpv4(ni)) {
                    return ni;
                }
            } catch (Exception ignored) {

            }
        }
        return null;
    }

    private static NetworkInterface pickRealIpv4() throws SocketException {
        Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
        while (nis.hasMoreElements()) {
            NetworkInterface ni = nis.nextElement();
            if (isSuitable(ni) && hasRealIpv4(ni)) {
                return ni;
            }
        }
        return null;
    }

    private static NetworkInterface pickAnyUpMulticastIpv4() throws SocketException {
        Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
        while (nis.hasMoreElements()) {
            NetworkInterface ni = nis.nextElement();
            if (!ni.isUp() || !ni.supportsMulticast() || ni.isLoopback()) continue;
            if (ni.isPointToPoint()) continue;
            if (hasRealIpv4(ni)) return ni;
        }
        return null;
    }

    private static boolean isSuitable(NetworkInterface ni) throws SocketException {
        if (!ni.isUp()
                || !ni.supportsMulticast()
                || ni.isLoopback()
                || ni.isVirtual()
                || ni.isPointToPoint()) {
            return false;
        }
        return !isVpnOrVirtual(ni);
    }

    private static boolean isVpnOrVirtual(NetworkInterface ni) {
        String name = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();

        String[] blacklist = {
                "virtual",
                "hyper-v", "hyperv",
                "vmware", "vmnet",
                "virtualbox", "vbox",
                "radmin",
                "vpn",
                "tap-windows", "tap",
                "tun", "wintun", "tun2socks",
                "wireguard", "openvpn",
                "wan miniport",
                "bluetooth",
                "kernel debug",
                "teredo", "isatap", "6to4", "ip-https",
                "wi-fi direct",
                "wfp ", "ndis ", "lightweight filter",
                "qos packet scheduler",
                "native wifi filter",
                "virtual wifi filter"
        };

        for (String bad : blacklist) {
            if (name.contains(bad)) return true;
        }
        return false;
    }

    private static boolean hasRealIpv4(NetworkInterface ni) {
        Enumeration<InetAddress> addrs = ni.getInetAddresses();
        while (addrs.hasMoreElements()) {
            InetAddress a = addrs.nextElement();
            if (a instanceof Inet4Address
                    && !a.isLoopbackAddress()
                    && !a.isLinkLocalAddress()
                    && !a.isAnyLocalAddress()) {
                return true;
            }
        }
        return false;
    }

    public static String resolveLocalIp(NetworkInterface ni) {
        if (ni == null) return "unknown";
        Enumeration<InetAddress> addrs = ni.getInetAddresses();
        while (addrs.hasMoreElements()) {
            InetAddress a = addrs.nextElement();
            if (a instanceof Inet4Address
                    && !a.isLoopbackAddress()
                    && !a.isLinkLocalAddress()) {
                return a.getHostAddress();
            }
        }
        return "unknown";
    }
}