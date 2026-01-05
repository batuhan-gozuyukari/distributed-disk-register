package com.example.family;

import family.Empty;
import family.FamilyServiceGrpc;
import family.FamilyView;
import family.NodeInfo;
import family.ChatMessage;
import family.StoredMessage;
import family.MessageId;
import family.StoreResult;
import family.StorageServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import com.example.family.command.Command;
import com.example.family.command.GetCommand;
import com.example.family.command.SetCommand;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NodeMain {
    private static final InMemoryStore STORE = new InMemoryStore();
    private static final CommandParser PARSER = new CommandParser();
    private static final DiskMessageStore DISK = new DiskMessageStore("messages");
    private static final Map<Integer, List<NodeInfo>> ID_TO_REPLICAS = new ConcurrentHashMap<>();
    private static final ToleranceConfig TCONF = new ToleranceConfig("tolerance.conf");
    private static final java.util.concurrent.atomic.AtomicInteger RR =new java.util.concurrent.atomic.AtomicInteger(0);

    private static final int START_PORT = 5555;
    private static final int PRINT_INTERVAL_SECONDS = 10;

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = findFreePort(START_PORT);

        NodeInfo self = NodeInfo.newBuilder()
                .setHost(host)
                .setPort(port)
                .build();

        NodeRegistry registry = new NodeRegistry();
        FamilyServiceImpl service = new FamilyServiceImpl(registry, self);

        Server server = ServerBuilder
                .forPort(port)
                .addService(service)
                .addService(new StorageServiceImpl(DISK))
                .build()
                .start();

                System.out.printf("Node started on %s:%d%n", host, port);

                
                if (port == START_PORT) {
                    startLeaderTextListener(registry, self);
                }

                discoverExistingNodes(host, port, registry, self);
                startFamilyPrinter(registry, self);
                startHealthChecker(registry, self);
                startMessageCountPrinter(DISK, self);

                server.awaitTermination();




    }

    private static void startLeaderTextListener(NodeRegistry registry, NodeInfo self) {
    
    new Thread(() -> {
        try (ServerSocket serverSocket = new ServerSocket(6666)) {
            System.out.printf("Leader listening for text on TCP %s:%d%n",
                    self.getHost(), 6666);

            while (true) {
                Socket client = serverSocket.accept();
                new Thread(() -> handleClientTextConnection(client, registry, self)).start();
            }

        } catch (IOException e) {
            System.err.println("Error in leader text listener: " + e.getMessage());
        }
    }, "LeaderTextListener").start();
}

private static void handleClientTextConnection(Socket client,
                                               NodeRegistry registry,
                                               NodeInfo self) {
        System.out.println("New TCP client connected: " + client.getRemoteSocketAddress());

    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(client.getInputStream()));
         BufferedWriter writer = new BufferedWriter(
            new OutputStreamWriter(client.getOutputStream()))) {

        String line;
        while ((line = reader.readLine()) != null) {
            String text = line.trim();
            if (text.isEmpty()) continue;


            try {
                Command cmd = PARSER.parse(text);

                if (cmd instanceof SetCommand) {
                SetCommand sc = (SetCommand) cmd;

                int tol = TCONF.readToleranceOrDefault(2);

                StoredMessage sm = StoredMessage.newBuilder()
                        .setId(sc.getId())
                        .setText(sc.getMessage())
                        .build();

                DISK.write(sc.getId(), sc.getMessage());

                List<NodeInfo> replicas = pickReplicas(registry, self, tol);
                
                boolean allOk = true;
                for (NodeInfo r : replicas) {
                    if (!grpcStore(r, sm)) {
                        allOk = false;
                    }
                }
                
                if (allOk) {
                    ID_TO_REPLICAS.put(sc.getId(), replicas);
                    writer.write("OK\n");
                } else {
                    writer.write("ERROR\n");
                }

                writer.flush();
                System.out.println("SET id=" + sc.getId() + " tol=" + tol + " replicas=" + replicas.size());


                } else if (cmd instanceof GetCommand) {
                    GetCommand gc = (GetCommand) cmd;
                    
                    String msg = DISK.read(gc.getId());
                    if (msg != null && !msg.isEmpty()) {
                        writer.write("OK " + msg + "\n");
                        writer.flush();
                        System.out.println("GET id=" + gc.getId() + " from=leader");
                        continue;
                    }

                    List<NodeInfo> replicas = ID_TO_REPLICAS.getOrDefault(gc.getId(), List.of());

                    String found = null;
                    for (NodeInfo r : replicas) {
                        found = grpcRetrieve(r, gc.getId());
                        if (found != null) break;
                    }
                     if (found == null) {
                         writer.write("NOT_FOUND\n");
                         System.out.println("GET id=" + gc.getId() + " NOT_FOUND");
                        } else {
                          writer.write("OK " + found + "\n");
                         System.out.println("GET id=" + gc.getId() + " from=replica");
                     }
                    writer.flush();

                } else {
                    writer.write("ERROR\n");
                    writer.flush();
                }

            } catch (Exception e) {
                writer.write("ERROR\n");
                writer.flush();
            }

        }

    } catch (IOException e) {
        System.err.println("TCP client handler error: " + e.getMessage());
    } finally {
        try { client.close(); } catch (IOException ignored) {}
    }
}

private static void broadcastToFamily(NodeRegistry registry,
                                      NodeInfo self,
                                      ChatMessage msg) {

    List<NodeInfo> members = registry.snapshot();

    for (NodeInfo n : members) {
        
        if (n.getHost().equals(self.getHost()) && n.getPort() == self.getPort()) {
            continue;
        }

        ManagedChannel channel = null;
        try {
            channel = ManagedChannelBuilder
                    .forAddress(n.getHost(), n.getPort())
                    .usePlaintext()
                    .build();

            FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                    FamilyServiceGrpc.newBlockingStub(channel);

            stub.receiveChat(msg);

            System.out.printf("Broadcasted message to %s:%d%n", n.getHost(), n.getPort());

        } catch (Exception e) {
            System.err.printf("Failed to send to %s:%d (%s)%n",
                    n.getHost(), n.getPort(), e.getMessage());
        } finally {
            if (channel != null) channel.shutdownNow();
        }
    }
}


    private static int findFreePort(int startPort) {
        int port = startPort;
        while (true) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                port++;
            }
        }
    }

    private static void discoverExistingNodes(String host,
                                              int selfPort,
                                              NodeRegistry registry,
                                              NodeInfo self) {

        for (int port = START_PORT; port < selfPort; port++) {
            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                        .forAddress(host, port)
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                        FamilyServiceGrpc.newBlockingStub(channel);

                FamilyView view = stub.join(self);
                registry.addAll(view.getMembersList());

                System.out.printf("Joined through %s:%d, family size now: %d%n",
                        host, port, registry.snapshot().size());

            } catch (Exception ignored) {
            } finally {
                if (channel != null) channel.shutdownNow();
            }
        }
    }

    private static void startFamilyPrinter(NodeRegistry registry, NodeInfo self) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            List<NodeInfo> members = registry.snapshot();
            System.out.println("======================================");
            System.out.printf("Family at %s:%d (me)%n", self.getHost(), self.getPort());
            System.out.println("Time: " + LocalDateTime.now());
            System.out.println("Members:");

            for (NodeInfo n : members) {
                boolean isMe = n.getHost().equals(self.getHost()) && n.getPort() == self.getPort();
                System.out.printf(" - %s:%d%s%n",
                        n.getHost(),
                        n.getPort(),
                        isMe ? " (me)" : "");
            }
            System.out.println("======================================");
        }, 3, PRINT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private static void startHealthChecker(NodeRegistry registry, NodeInfo self) {
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    scheduler.scheduleAtFixedRate(() -> {
        List<NodeInfo> members = registry.snapshot();

        for (NodeInfo n : members) {
            
            if (n.getHost().equals(self.getHost()) && n.getPort() == self.getPort()) {
                continue;
            }

            ManagedChannel channel = null;
            try {
                channel = ManagedChannelBuilder
                        .forAddress(n.getHost(), n.getPort())
                        .usePlaintext()
                        .build();

                FamilyServiceGrpc.FamilyServiceBlockingStub stub =
                        FamilyServiceGrpc.newBlockingStub(channel);

                
                
                stub.getFamily(Empty.newBuilder().build());

            } catch (Exception e) {
                
                System.out.printf("Node %s:%d unreachable, removing from family%n",
                        n.getHost(), n.getPort());
                registry.remove(n);
            } finally {
                if (channel != null) {
                    channel.shutdownNow();
                }
            }
        }

    }, 5, 10, TimeUnit.SECONDS); 
}
    private static List<NodeInfo> pickReplicas(NodeRegistry registry, NodeInfo self, int k) {
        List<NodeInfo> all = registry.snapshot();
        List<NodeInfo> members = new ArrayList<>();
    
        // kendimizi çıkar
        for (NodeInfo n : all) {
            if (n.getHost().equals(self.getHost()) && n.getPort() == self.getPort()) continue;
            members.add(n);
        }
    
        if (members.isEmpty()) return List.of();
    
        if (k > members.size()) k = members.size();
    
        int start = Math.floorMod(RR.getAndIncrement(), members.size());
    
        List<NodeInfo> result = new ArrayList<>();
        for(int i = 0; i < k; i++) {
            int idx = (start + i) % members.size();
            result.add(members.get(idx));
        }
        return result;
    }
    
    private static String grpcRetrieve(NodeInfo member, int id) {
        ManagedChannel ch = null;
        try {
            ch = ManagedChannelBuilder
                    .forAddress(member.getHost(), member.getPort())
                    .usePlaintext()
                    .build();
    
            StorageServiceGrpc.StorageServiceBlockingStub stub =
                    StorageServiceGrpc.newBlockingStub(ch);
    
            StoredMessage m = stub.retrieve(MessageId.newBuilder().setId(id).build());
    
            if (m.getText() == null || m.getText().isEmpty()) return null;
            return m.getText();
        } catch (Exception e) {
            return null;
        } finally {
            if (ch != null) ch.shutdownNow();
        }
    }
    
    private static void startMessageCountPrinter(DiskMessageStore disk, NodeInfo self) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            int count = disk.countMessages();
            System.out.printf(
                    "[COUNT] Node %s:%d stores %d messages%n",
                    self.getHost(),
                    self.getPort(),
                    count
            );
        }, 5, 10, TimeUnit.SECONDS);
    }
}
