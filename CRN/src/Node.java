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
    private java.util.HashMap<String, String> store = new java.util.HashMap<>();
    private java.util.HashMap<String, String> nodeAddresses = new java.util.HashMap<>();
    private java.util.Stack<String> relayStack = new java.util.Stack<>();
    private String myAddress;

    public void setNodeName(String nodeName) throws Exception {
        if (!nodeName.startsWith("N:")) {
            nodeName = "N:" + nodeName;
        }
        this.nodeName = nodeName;
    }

    public void openPort(int portNumber) throws Exception {
        this.socket = new DatagramSocket(portNumber);

        String ip;
        try {
            ip = java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            ip = "127.0.0.1";
        }

        myAddress = ip + ":" + portNumber;

        if (nodeName != null) {
            nodeAddresses.put(nodeName, myAddress);
        }
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

        int valueEnd = pos;

        while (valueEnd < message.length()
                && message.charAt(valueEnd) != ' ') {
            valueEnd++;
        }

        if (valueEnd >= message.length()) {
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
                    response = txid + " F Y ";
                } else if (isResponsibleFor(key)) {
                    response = txid + " F N ";
                } else {
                    response = txid + " F ? ";
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
                String response;

                if (!key.startsWith("D:") && !key.startsWith("N:")) {

                    response = txid + " X X ";

                } else if (key.startsWith("N:")) {

                    replacing = nodeAddresses.containsKey(key);

                    nodeAddresses.put(key, value);

                    response = txid + (replacing ? " X R " : " X A ");

                } else {

                    replacing = store.containsKey(key);
                    if (replacing) {
                        store.put(key, value);

                        response = txid + " X R ";

                    } else if (isResponsibleFor(key)) {

                        store.put(key, value);

                        response = txid + " X A ";

                    } else {

                        response = txid + " X X ";
                    }
                }

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
                } else if (isResponsibleFor(key)) {
                    response = txid + " S N ";
                } else {
                    response = txid + " S ? ";
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

                String outerTxid = message.substring(0, 2);

                ParsedString targetPart = decodeString(message, 5);

                String targetNode = targetPart.value;

                String innerMessage = message.substring(targetPart.nextIndex);

                System.out.println("Relay target: [" + targetNode + "]");
                System.out.println("Relaying: [" + innerMessage + "]");

                String address = nodeAddresses.get(targetNode);

                if (address == null) {
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

                socket.setSoTimeout(5000);

                try {

                    byte[] responseBuffer = new byte[4096];

                    DatagramPacket responsePacket =
                            new DatagramPacket(responseBuffer, responseBuffer.length);

                    socket.receive(responsePacket);

                    String response = new String(
                            responsePacket.getData(),
                            0,
                            responsePacket.getLength(),
                            StandardCharsets.UTF_8
                    );

                    if (response.length() >= 2) {

                        String fixedResponse =
                                outerTxid + response.substring(2);

                        byte[] relayResponse =
                                fixedResponse.getBytes(StandardCharsets.UTF_8);

                        DatagramPacket back = new DatagramPacket(
                                relayResponse,
                                relayResponse.length,
                                packet.getAddress(),
                                packet.getPort()
                        );

                        socket.send(back);

                        System.out.println("Relay response: [" + fixedResponse + "]");
                    }

                } catch (SocketTimeoutException e) {

                    System.out.println("Relay timeout");
                }
            }

            if (message.length() >= 4 && message.substring(2, 4).equals(" N")) {
                String txid = message.substring(0, 2);

                String hash = message.substring(5).trim();

                String response = txid + " O ";

                int count = 0;

                for (String node : sortNodes(hash)) {
                    if (count >= 3) {
                        break;
                    }

                    String address = nodeAddresses.get(node);

                    if (address == null) {
                        continue;
                    }

                    response += encodeString(node);
                    response += encodeString(address);

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

                if (!key.startsWith("D:") && !key.startsWith("N:")) {
                    response = txid + " D X ";
                } else if (store.containsKey(key)) {
                    if (store.get(key).equals(currentValue)) {
                        store.put(key, newValue);
                        response = txid + " D R ";
                    } else {
                        response = txid + " D N ";
                    }
                } else if (isResponsibleFor(key)) {
                    store.put(key, newValue);
                    response = txid + " D A ";
                } else {
                    response = txid + " D X ";
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

    private boolean isResponsibleFor(String key) throws Exception {

        byte[] keyHash = HashID.computeHashID(key);

        int myDistance =
                HashID.getDistance(
                        HashID.computeHashID(nodeName),
                        keyHash
                );

        int closer = 0;

        for (String node : nodeAddresses.keySet()) {

            int distance =
                    HashID.getDistance(
                            HashID.computeHashID(node),
                            keyHash
                    );

            if (distance < myDistance) {
                closer++;
            }
        }

        return closer < 3;
    }



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

        String originalMessage = new String(
                originalPacket.getData(),
                0,
                originalPacket.getLength(),
                StandardCharsets.UTF_8
        );

        String targetNode = null;

        for (String name : nodeAddresses.keySet()) {
            String address = nodeAddresses.get(name);
            String[] parts = address.split(":");

            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            if (originalPacket.getAddress().getHostAddress().equals(ip)
                    && originalPacket.getPort() == port) {
                targetNode = name;
                break;
            }
        }

        if (targetNode == null) {
            socket.send(originalPacket);
            return;
        }

        String messageToWrap = originalMessage;

        for (int i = relayStack.size() - 1; i >= 0; i--) {
            String relayTarget = targetNode;

            if (i < relayStack.size() - 1) {
                relayTarget = relayStack.get(i + 1);
            }

            if (!relayTarget.startsWith("N:")) {
                relayTarget = "N:" + relayTarget;
            }

            String txid = messageToWrap.substring(0, 2);

            messageToWrap = txid + " V " + encodeString(relayTarget) + messageToWrap;
        }

        String firstRelay = relayStack.firstElement();

        if (!firstRelay.startsWith("N:")) {
            firstRelay = "N:" + firstRelay;
        }

        String firstRelayAddress = nodeAddresses.get(firstRelay);

        if (firstRelayAddress == null) {
            socket.send(originalPacket);
            return;
        }

        String[] parts = firstRelayAddress.split(":");
        String ip = parts[0];
        int port = Integer.parseInt(parts[1]);

        byte[] out = messageToWrap.getBytes(StandardCharsets.UTF_8);

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

        learnNearest(key);

        for (String address : nodeAddresses.values()) {
            if (address.equals(myAddress)) {
                continue;
            }
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

            socket.setSoTimeout(500);

            for (int attempt = 0; attempt < 3; attempt++) {
                System.out.println("Exists attempt: " + (attempt + 1));

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

                    System.out.println("Exists response: [" + response + "]");

                    if (!response.startsWith(txid)) {
                        continue;
                    }

                    if (response.length() >= 6 &&
                            response.substring(2, 5).equals(" F ")) {

                        char code = response.charAt(5);

                        if (code == 'Y') {
                            return true;
                        }

                        if (code == 'N') {
                            return false;
                        }

                        if (code == '?') {
                            break;
                        }
                    }
                }
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
    private byte[] stringToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];

        for (int i = 0; i < hex.length(); i += 2) {
            int first = Character.digit(hex.charAt(i), 16);
            int second = Character.digit(hex.charAt(i + 1), 16);

            bytes[i / 2] = (byte) ((first << 4) + second);
        }

        return bytes;
    }
    private java.util.List<String> sortNodes(String hash) throws Exception {

        byte[] target = stringToBytes(hash);

        java.util.ArrayList<String> nodes =
                new java.util.ArrayList<>(nodeAddresses.keySet());

        nodes.sort((a, b) -> {
            try {

                int distanceA =
                        HashID.getDistance(HashID.computeHashID(a), target);

                int distanceB =
                        HashID.getDistance(HashID.computeHashID(b), target);

                return Integer.compare(distanceA, distanceB);

            } catch (Exception e) {
                return 0;
            }
        });

        return nodes;
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

            sendWithRelay(packet);

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

                if (response.length() >= 5 &&
                        response.substring(2, 5).equals(" O ")) {

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
            if (address.equals(myAddress)) {
                continue;
            }
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
        handleIncomingMessages(100);

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
            if (address.equals(myAddress)) {
                continue;
            }
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

        if (nodeAddresses.isEmpty()) {
            if (store.containsKey(key)) {
                if (store.get(key).equals(currentValue)) {
                    store.put(key, newValue);
                    return true;
                }
                return false;
            }

            store.put(key, newValue);
            return true;
        }

        learnNearest(key);

        for (String address : nodeAddresses.values()) {
            if (address.equals(myAddress)) {
                continue;
            }
            String[] parts = address.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            String txid = nextTxID();

            String request = txid + " C "
                    + encodeString(key)
                    + encodeString(currentValue)
                    + encodeString(newValue);

            byte[] out = request.getBytes(StandardCharsets.UTF_8);

            DatagramPacket packet = new DatagramPacket(
                    out,
                    out.length,
                    java.net.InetAddress.getByName(ip),
                    port
            );

            socket.setSoTimeout(500);

            for (int attempt = 0; attempt < 3; attempt++) {

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

                    if (!response.startsWith(txid)) {
                        continue;
                    }

                    if (response.length() >= 6 &&
                            response.substring(2, 5).equals(" D ")) {

                        char code = response.charAt(5);

                        if (code == 'R' || code == 'A') {
                            return true;
                        }

                        if (code == 'N' || code == 'X') {
                            return false;
                        }
                    }
                }
            }
        }

        return false;
    }
}
