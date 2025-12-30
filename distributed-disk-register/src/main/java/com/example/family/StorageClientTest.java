package com.example.family;

import family.MessageId;
import family.StoredMessage;
import family.StoreResult;
import family.StorageServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class StorageClientTest {
    public static void main(String[] args) {
        ManagedChannel ch = ManagedChannelBuilder
                .forAddress("127.0.0.1", 5555) // lider node portu
                .usePlaintext()
                .build();

        StorageServiceGrpc.StorageServiceBlockingStub stub =
                StorageServiceGrpc.newBlockingStub(ch);

        // 1) Store RPC
        StoreResult r = stub.store(
                StoredMessage.newBuilder()
                        .setId(77)
                        .setText("grpc-selam")
                        .build()
        );
        System.out.println("store ok=" + r.getOk() + " err=" + r.getError());

        // 2) Retrieve RPC
        StoredMessage m = stub.retrieve(
                MessageId.newBuilder().setId(77).build()
        );
        System.out.println("retrieve id=" + m.getId() + " text=" + m.getText());

        ch.shutdownNow();
    }
}
