// IN2011 Computer Networks
// Coursework 2024/2025
//
// Submission by
//  Bahja Suliman
//  240022311
//  Bahja.Suliman@city.ac.uk


// DO NOT EDIT starts
// This gives the interface that your code must implement.
// These descriptions are intended to help you understand how the interface
// will be used. See the RFC for how the protocol works.

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

interface NodeInterface {

    /* These methods configure your node.
     * They must both be called once after the node has been created but
     * before it is used. */
    
    // Set the name of the node.
    public void setNodeName(String nodeName) throws Exception;

    // Open a UDP port for sending and receiving messages.
    public void openPort(int portNumber) throws Exception;


    /*
     * These methods query and change how the network is used.
     */

    // Handle all incoming messages.
    // If you wait for more than delay miliseconds and
    // there are no new incoming messages return.
    // If delay is zero then wait for an unlimited amount of time.
    public void handleIncomingMessages(int delay) throws Exception;
    
    // Determines if a node can be contacted and is responding correctly.
    // Handles any messages that have arrived.
    public boolean isActive(String nodeName) throws Exception;

    // You need to keep a stack of nodes that are used to relay messages.
    // The base of the stack is the first node to be used as a relay.
    // The first node must relay to the second node and so on.
    
    // Adds a node name to a stack of nodes used to relay all future messages.
    public void pushRelay(String nodeName) throws Exception;

    // Pops the top entry from the stack of nodes used for relaying.
    // No effect if the stack is empty
    public void popRelay() throws Exception;
    

    /*
     * These methods provide access to the basic functionality of
     * CRN-25 network.
     */

    // Checks if there is an entry in the network with the given key.
    // Handles any messages that have arrived.
    public boolean exists(String key) throws Exception;
    
    // Reads the entry stored in the network for key.
    // If there is a value, return it.
    // If there isn't a value, return null.
    // Handles any messages that have arrived.
    public String read(String key) throws Exception;

    // Sets key to be value.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean write(String key, String value) throws Exception;

    // If key is set to currentValue change it to newValue.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean CAS(String key, String currentValue, String newValue) throws Exception;

}
// DO NOT EDIT ends

// Complete this!
public class Node implements NodeInterface {

    private String nodeName;
    private DatagramSocket socket;

    public void setNodeName(String nodeName) throws Exception {
        this.nodeName = nodeName;
    }

    public void openPort(int portNumber) throws Exception {
        this.socket = new DatagramSocket(portNumber);
    }

    private static class ParsedString {
        String value;
        int nextIndex;

        ParsedString(String value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }
    private ParsedString decodeString(String message, int start) throws Exception {
        int spacePos = message.indexOf(' ', start);
        if (spacePos == -1) {
            throw new Exception("Bad CRN string: missing space count");
        }

        int numberOfSpaces = Integer.parseInt(message.substring(start, spacePos));

        int valueStart = spacePos + 1;
        int pos = valueStart;

        for (int i = 0; i < numberOfSpaces; i++) {
            pos = message.indexOf(' ', pos);
            if (pos == -1) {
                throw new Exception("Bad CRN string: not enough spaces");
            }
            pos++;
        }

        int valueEnd = message.indexOf(' ', pos);
        if (valueEnd == -1) {
            throw new Exception("Bad CRN string: missing final space");
        }

        String value = message.substring(valueStart, valueEnd);
        return new ParsedString(value, valueEnd + 1);
    }
    public void handleIncomingMessages(int delay) throws Exception {
        if (socket == null) {
            throw new Exception("Port not opened");
        }

        socket.setSoTimeout(delay == 0 ? 0 : 250);
        long startTime = System.currentTimeMillis();

        while (true) {

            if (delay != 0 && System.currentTimeMillis() - startTime >= delay) {
                System.out.println("handleIncomingMessages timeout reached");
                return;
            }

            byte[] buffer = new byte[4096];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(packet);
            } catch (SocketTimeoutException e) {
                continue;
            }

            String message = new String(
                    packet.getData(),
                    0,
                    packet.getLength(),
                    StandardCharsets.UTF_8
            );

            System.out.println("Received: [" + message + "]");

            if (message.length() >= 4 && message.substring(2, 4).equals(" E")) {
                String txid = message.substring(0, 2);

                ParsedString keyPart = decodeString(message, 5);
                String key = keyPart.value;

                String response;
                if (store.containsKey(key)) {
                    response = txid + " F Y";
                } else {
                    response = txid + " F N";
                }

                byte[] out = response.getBytes(StandardCharsets.UTF_8);
                DatagramPacket reply = new DatagramPacket(
                        out,
                        out.length,
                        packet.getAddress(),
                        packet.getPort()
                );

                socket.send(reply);

                System.out.println("E key: [" + key + "]");
                System.out.println("Sent: [" + response + "]");
            }

            if (message.length() >= 4 && message.substring(2, 4).equals(" G")) {
                String txid = message.substring(0, 2);
                String response = txid + " H " + encodeString(nodeName);

                byte[] out = response.getBytes(StandardCharsets.UTF_8);
                DatagramPacket reply = new DatagramPacket(
                        out,
                        out.length,
                        packet.getAddress(),
                        packet.getPort()
                );

                socket.send(reply);

                System.out.println("Sent: [" + response + "]");
            }

            if (message.length() >= 4 && message.substring(2, 4).equals(" W")) {
                String txid = message.substring(0, 2);

                ParsedString keyPart = decodeString(message, 5);
                ParsedString valuePart = decodeString(message, keyPart.nextIndex);

                String key = keyPart.value;
                String value = valuePart.value;

                boolean replacing;

                if (key.startsWith("N:")) {
                    replacing = nodeAddresses.containsKey(key);
                    nodeAddresses.put(key, value);
                } else {
                    replacing = store.containsKey(key);
                    store.put(key, value);
                }

                System.out.println("Stored nodes: " + nodeAddresses);

                String code = replacing ? "R" : "A";
                String response = txid + " X " + code;

                byte[] out = response.getBytes(StandardCharsets.UTF_8);
                DatagramPacket reply = new DatagramPacket(
                        out,
                        out.length,
                        packet.getAddress(),
                        packet.getPort()
                );

                socket.send(reply);

                System.out.println("W key: [" + key + "]");
                System.out.println("W value: [" + value + "]");
                System.out.println("Sent: [" + response + "]");
            }

            if (message.length() >= 4 && message.substring(2, 4).equals(" R")) {
                String txid = message.substring(0, 2);

                ParsedString keyPart = decodeString(message, 5);
                String key = keyPart.value;

                String response;
                if (store.containsKey(key)) {
                    response = txid + " S Y " + encodeString(store.get(key));
                } else {
                    response = txid + " S N";
                }

                byte[] out = response.getBytes(StandardCharsets.UTF_8);
                DatagramPacket reply = new DatagramPacket(
                        out,
                        out.length,
                        packet.getAddress(),
                        packet.getPort()
                );

                socket.send(reply);

                System.out.println("R key: [" + key + "]");
                System.out.println("Sent: [" + response + "]");
            }

            if (message.length() >= 4 && message.substring(2, 4).equals(" V")) {
                ParsedString targetPart = decodeString(message, 5);
                ParsedString innerPart = decodeString(message, targetPart.nextIndex);

                String targetNode = targetPart.value;
                String innerMessage = innerPart.value;

                System.out.println("Relay target: [" + targetNode + "]");
                System.out.println("Relaying: [" + innerMessage + "]");

                String address = nodeAddresses.get(targetNode);
                if (address == null) {
                    System.out.println("Unknown relay target");
                    continue;
                }

                String[] parts = address.split(":");
                String ip = parts[0];
                int port = Integer.parseInt(parts[1]);

                byte[] out = innerMessage.getBytes(StandardCharsets.UTF_8);
                DatagramPacket forward = new DatagramPacket(
                        out,
                        out.length,
                        java.net.InetAddress.getByName(ip),
                        port
                );

                socket.send(forward);
            }

            if (message.length() >= 4 && message.substring(2, 4).equals(" N")) {
                String txid = message.substring(0, 2);

                String response = txid + " O";

                int count = 0;
                for (String key : nodeAddresses.keySet()) {
                    if (count >= 3) {
                        break;
                    }

                    String value = nodeAddresses.get(key);
                    response += " " + encodeString(key) + encodeString(value);
                    count++;
                }

                byte[] out = response.getBytes(StandardCharsets.UTF_8);
                DatagramPacket reply = new DatagramPacket(
                        out,
                        out.length,
                        packet.getAddress(),
                        packet.getPort()
                );

                socket.send(reply);

                System.out.println("Sent nearest response: [" + response + "]");
            }

            if (message.length() >= 4 && message.substring(2, 4).equals(" C")) {
                String txid = message.substring(0, 2);

                ParsedString keyPart = decodeString(message, 5);
                ParsedString currentPart = decodeString(message, keyPart.nextIndex);
                ParsedString newPart = decodeString(message, currentPart.nextIndex);

                String key = keyPart.value;
                String currentValue = currentPart.value;
                String newValue = newPart.value;

                String response;

                if (store.containsKey(key)) {
                    if (store.get(key).equals(currentValue)) {
                        store.put(key, newValue);
                        response = txid + " D R";
                    } else {
                        response = txid + " D N";
                    }
                } else {
                    store.put(key, newValue);
                    response = txid + " D A";
                }

                byte[] out = response.getBytes(StandardCharsets.UTF_8);
                DatagramPacket reply = new DatagramPacket(
                        out,
                        out.length,
                        packet.getAddress(),
                        packet.getPort()
                );

                socket.send(reply);

                System.out.println("CAS key: [" + key + "]");
                System.out.println("Current: [" + currentValue + "]");
                System.out.println("New: [" + newValue + "]");
                System.out.println("Sent: [" + response + "]");
            }
        }
    }

    private String encodeString(String s) {
        int spaces = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ' ') {
                spaces++;
            }
        }
        return spaces + " " + s + " ";
    }
    private java.util.HashMap<String, String> store = new java.util.HashMap<>();

    private java.util.HashMap<String, String> nodeAddresses = new java.util.HashMap<>();

    private java.util.Stack<String> relayStack = new java.util.Stack<>();


    public boolean isActive(String nodeName) throws Exception {

        if (!nodeAddresses.containsKey(nodeName)) {
            return false;
        }

        String address = nodeAddresses.get(nodeName);
        String[] parts = address.split(":");
        String ip = parts[0];
        int port = Integer.parseInt(parts[1]);

        String txid = nextTxID();
        String request = txid + " G";

        byte[] out = request.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(
                out,
                out.length,
                java.net.InetAddress.getByName(ip),
                port
        );

        byte[] buffer = new byte[4096];
        DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);

        socket.setSoTimeout(5000);

        for (int attempt = 0; attempt < 3; attempt++) {
            System.out.println("isActive attempt: " + (attempt + 1));
            sendWithRelay(packet);

            try {
                socket.receive(responsePacket);

                String response = new String(
                        responsePacket.getData(),
                        0,
                        responsePacket.getLength(),
                        StandardCharsets.UTF_8
                );

                System.out.println("isActive response: [" + response + "]");

                if (response.length() >= 4 &&
                        response.substring(0, 2).equals(txid) &&
                        response.substring(2, 4).equals(" H")) {

                    return true;
                }

            } catch (SocketTimeoutException e) {
                System.out.println("isActive timeout, retrying...");
            }
        }

        return false;
    }

    public void pushRelay(String nodeName) throws Exception {
        relayStack.push(nodeName);
    }

    public void popRelay() throws Exception {
        if (!relayStack.isEmpty()) {
            relayStack.pop();
        }
    }
    private void sendWithRelay(DatagramPacket originalPacket) throws Exception {

        if (relayStack.isEmpty()) {
            socket.send(originalPacket);
            return;
        }

        // take first relay
        String relayNode = relayStack.firstElement();
        String address = nodeAddresses.get(relayNode);

        String[] parts = address.split(":");
        String ip = parts[0];
        int port = Integer.parseInt(parts[1]);

        String originalMessage = new String(
                originalPacket.getData(),
                0,
                originalPacket.getLength(),
                StandardCharsets.UTF_8
        );

        // wrap message
        String wrapped = "AA V " + encodeString(originalMessage);

        byte[] out = wrapped.getBytes(StandardCharsets.UTF_8);

        DatagramPacket relayPacket = new DatagramPacket(
                out,
                out.length,
                java.net.InetAddress.getByName(ip),
                port
        );

        socket.send(relayPacket);
    }

    public boolean exists(String key) throws Exception {
        if (store.containsKey(key)) {
            return true;
        }

        if (nodeAddresses.isEmpty()) {
            return false;
        }

        String address = nodeAddresses.values().iterator().next();
        String[] parts = address.split(":");
        String ip = parts[0];
        int port = Integer.parseInt(parts[1]);

        String txid = nextTxID();
        String request = txid + " E " + encodeString(key);

        byte[] out = request.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(
                out,
                out.length,
                java.net.InetAddress.getByName(ip),
                port
        );

        byte[] buffer = new byte[4096];
        DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);

        socket.setSoTimeout(5000);

        for (int attempt = 0; attempt < 3; attempt++) {
            System.out.println("Exists attempt: " + (attempt + 1));
            socket.send(packet);

            try {
                socket.receive(responsePacket);

                String response = new String(
                        responsePacket.getData(),
                        0,
                        responsePacket.getLength(),
                        StandardCharsets.UTF_8
                );

                System.out.println("Exists response: [" + response + "]");

                if (response.length() >= 6 &&
                        response.substring(0, 2).equals(txid) &&
                        response.substring(2, 5).equals(" F ")) {

                    char code = response.charAt(5);
                    return code == 'Y';
                }

            } catch (SocketTimeoutException e) {
                System.out.println("Exists timeout, retrying...");
            }
        }

        return false;
    }
    private String hashHex(String key) throws Exception {
        byte[] hash = HashID.computeHashID(key);
        StringBuilder sb = new StringBuilder();

        for (byte b : hash) {
            sb.append(String.format("%02x", b & 0xff));
        }

        return sb.toString();
    }

    private void learnNearest(String key) throws Exception {
        if (nodeAddresses.isEmpty()) {
            return;
        }

        String hash = hashHex(key);

        java.util.ArrayList<String> addresses =
                new java.util.ArrayList<>(nodeAddresses.values());

        for (String address : addresses) {
            String[] parts = address.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            String txid = nextTxID();
            String request = txid + " N " + hash;

            byte[] out = request.getBytes(StandardCharsets.UTF_8);

            DatagramPacket packet = new DatagramPacket(
                    out,
                    out.length,
                    java.net.InetAddress.getByName(ip),
                    port
            );

            socket.setSoTimeout(500);

            socket.send(packet);

            long endTime = System.currentTimeMillis() + 2000;

            while (System.currentTimeMillis() < endTime) {
                byte[] buffer = new byte[4096];
                DatagramPacket responsePacket =
                        new DatagramPacket(buffer, buffer.length);

                try {
                    socket.receive(responsePacket);
                } catch (SocketTimeoutException e) {
                    continue;
                }

                String response = new String(
                        responsePacket.getData(),
                        0,
                        responsePacket.getLength(),
                        StandardCharsets.UTF_8
                );

                if (!response.startsWith(txid)) {
                    continue;
                }

                if (response.length() >= 4 &&
                        response.substring(2, 4).equals(" O")) {

                    int index = 5;

                    while (index < response.length()) {
                        ParsedString nodePart = decodeString(response, index);
                        ParsedString addrPart = decodeString(response, nodePart.nextIndex);

                        nodeAddresses.put(nodePart.value, addrPart.value);

                        index = addrPart.nextIndex;
                    }

                    return;
                }
            }
        }
    }



    public String read(String key) throws Exception {
        if (store.containsKey(key)) {
            return store.get(key);
        }

        if (nodeAddresses.isEmpty()) {
            return null;
        }
        learnNearest(key);

        for (String address : nodeAddresses.values()) {
            String[] parts = address.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            String txid = nextTxID();
            String request = txid + " R " + encodeString(key);

            byte[] out = request.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(
                    out,
                    out.length,
                    java.net.InetAddress.getByName(ip),
                    port
            );

            socket.setSoTimeout(500);

            for (int attempt = 0; attempt < 3; attempt++) {
                System.out.println("Read attempt: " + (attempt + 1));
                sendWithRelay(packet);

                long endTime = System.currentTimeMillis() + 5000;

                while (System.currentTimeMillis() < endTime) {
                    byte[] buffer = new byte[4096];
                    DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);

                    try {
                        socket.receive(responsePacket);
                    } catch (SocketTimeoutException e) {
                        continue;
                    }

                    String response = new String(
                            responsePacket.getData(),
                            0,
                            responsePacket.getLength(),
                            StandardCharsets.UTF_8
                    );

                    System.out.println("Read response: [" + response + "]");

                    if (!response.startsWith(txid)) {
                        continue;
                    }

                    if (response.length() >= 6 && response.substring(2, 5).equals(" S ")) {
                        char code = response.charAt(5);

                        if (code == 'Y') {
                            ParsedString valuePart = decodeString(response, 7);
                            return valuePart.value;
                        }

                        if (code == 'N' || code == '?') {
                            break;
                        }
                    }
                }
            }

            Thread.sleep(500);
        }

        return null;
    }


    public boolean write(String key, String value) throws Exception {

        // if it's a node address, store locally
        if (key.startsWith("N:")) {
            nodeAddresses.put(key, value);
            return true;
        }

        // if no nodes known, fallback to local
        if (nodeAddresses.isEmpty()) {
            store.put(key, value);
            return true;
        }

        learnNearest(key);

        for (String address : nodeAddresses.values()) {

            String[] parts = address.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            String txid = nextTxID();
            String request = txid + " W " +
                    encodeString(key) +
                    encodeString(value);

            byte[] out = request.getBytes(StandardCharsets.UTF_8);

            DatagramPacket packet = new DatagramPacket(
                    out,
                    out.length,
                    java.net.InetAddress.getByName(ip),
                    port
            );

            socket.setSoTimeout(500);

            for (int attempt = 0; attempt < 3; attempt++) {

                System.out.println("Write attempt: " + (attempt + 1));

                sendWithRelay(packet);

                long endTime = System.currentTimeMillis() + 5000;

                while (System.currentTimeMillis() < endTime) {

                    byte[] buffer = new byte[4096];
                    DatagramPacket responsePacket =
                            new DatagramPacket(buffer, buffer.length);

                    try {
                        socket.receive(responsePacket);
                    } catch (SocketTimeoutException e) {
                        continue;
                    }

                    String response = new String(
                            responsePacket.getData(),
                            0,
                            responsePacket.getLength(),
                            StandardCharsets.UTF_8
                    );

                    System.out.println("Write response: [" + response + "]");

                    // ignore unrelated packets
                    if (!response.startsWith(txid)) {
                        continue;
                    }

                    if (response.length() >= 6 &&
                            response.substring(2, 5).equals(" X ")) {

                        char code = response.charAt(5);

                        if (code == 'A' || code == 'R') {
                            return true;
                        }

                        if (code == 'X') {
                            break;
                        }
                    }
                }
            }

            Thread.sleep(500);
        }

        return false;
    }
    
    private int txCounter = 0;
    private String nextTxID() {
        int id = txCounter++;
        char c1 = (char) ('A' + (id / 26) % 26);
        char c2 = (char) ('A' + id % 26);
        return "" + c1 + c2;
    }
    public boolean CAS(String key, String currentValue, String newValue) throws Exception {
        if (store.containsKey(key)) {
            if (store.get(key).equals(currentValue)) {
                store.put(key, newValue);
                return true;
            } else {
                return false;
            }
        } else {
            store.put(key, newValue);
            return true;
        }
    }
}
