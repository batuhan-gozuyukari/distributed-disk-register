package com.example.family;

import family.MessageId;
import family.StoredMessage;
import family.StoreResult;
import family.StorageServiceGrpc;
import io.grpc.stub.StreamObserver;

public class StorageServiceImpl extends StorageServiceGrpc.StorageServiceImplBase {

    private final DiskMessageStore disk;

    public StorageServiceImpl(DiskMessageStore disk) {
        this.disk = disk;
    }

    @Override
    public void store(StoredMessage request, StreamObserver<StoreResult> responseObserver) {
        try {
            disk.write(request.getId(), request.getText());
            StoreResult res = StoreResult.newBuilder()
                    .setOk(true)
                    .build();
            responseObserver.onNext(res);
            responseObserver.onCompleted();
        } catch (Exception e) {
            StoreResult res = StoreResult.newBuilder()
                    .setOk(false)
                    .setError(e.getMessage() == null ? "ERROR" : e.getMessage())
                    .build();
            responseObserver.onNext(res);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void retrieve(MessageId request, StreamObserver<StoredMessage> responseObserver) {
        try {
            String msg = disk.read(request.getId());
            if (msg == null) {
                StoredMessage res = StoredMessage.newBuilder()
                        .setId(request.getId())
                        .setText("")
                        .build();
                responseObserver.onNext(res);
                responseObserver.onCompleted();
                return;
            }

            StoredMessage res = StoredMessage.newBuilder()
                    .setId(request.getId())
                    .setText(msg)
                    .build();
            responseObserver.onNext(res);
            responseObserver.onCompleted();
        } catch (Exception e) {
            StoredMessage res = StoredMessage.newBuilder()
                    .setId(request.getId())
                    .setText("")
                    .build();
            responseObserver.onNext(res);
            responseObserver.onCompleted();
        }
    }
}
