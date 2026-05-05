public class ManualReadClient {

    public static void main(String[] args) throws Exception {
        Node node = new Node();
        node.setNodeName("N:client");
        node.openPort(20111);

        node.write("N:test0", "127.0.0.1:20110");
        System.out.println("Active: " + node.isActive("N:test0"));

        boolean ok = node.write("D:test", "network works");
        System.out.println("Write returned: " + ok);

        String value = node.read("D:Juliet-0");
        System.out.println("Read returned: " + value);

        System.out.println("Exists Juliet: " + node.exists("D:Juliet-0"));
        System.out.println("Exists missing: " + node.exists("D:nope"));
    }
    }