/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.client.impl.mqclient;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.consumer.AckCallback;
import org.apache.rocketmq.client.consumer.AckResult;
import org.apache.rocketmq.client.consumer.NotifyResult;
import org.apache.rocketmq.client.consumer.PopCallback;
import org.apache.rocketmq.client.consumer.PopResult;
import org.apache.rocketmq.client.consumer.PullCallback;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.OffsetNotFoundException;
import org.apache.rocketmq.client.impl.ClientRemotingProcessor;
import org.apache.rocketmq.client.impl.CommunicationMode;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.admin.MqClientAdminImpl;
import org.apache.rocketmq.client.impl.consumer.PullResultExt;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.ObjectCreator;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageBatch;
import org.apache.rocketmq.common.message.MessageClientIDSetter;
import org.apache.rocketmq.common.message.MessageDecoder;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.logging.org.slf4j.Logger;
import org.apache.rocketmq.logging.org.slf4j.LoggerFactory;
import org.apache.rocketmq.remoting.InvokeCallback;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.RemotingClient;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.ResponseFuture;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RequestCode;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.LockBatchRequestBody;
import org.apache.rocketmq.remoting.protocol.body.LockBatchResponseBody;
import org.apache.rocketmq.remoting.protocol.body.UnlockBatchRequestBody;
import org.apache.rocketmq.remoting.protocol.header.AckMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.ChangeInvisibleTimeRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.ConsumerSendMsgBackRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.GetConsumerListByGroupRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.GetConsumerListByGroupResponseBody;
import org.apache.rocketmq.remoting.protocol.header.GetMaxOffsetRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.GetMaxOffsetResponseHeader;
import org.apache.rocketmq.remoting.protocol.header.GetMinOffsetRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.GetMinOffsetResponseHeader;
import org.apache.rocketmq.remoting.protocol.header.HeartbeatRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.LockBatchMqRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.NotificationRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.NotificationResponseHeader;
import org.apache.rocketmq.remoting.protocol.header.PopMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.PullMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.QueryConsumerOffsetRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.QueryConsumerOffsetResponseHeader;
import org.apache.rocketmq.remoting.protocol.header.RecallMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.RecallMessageResponseHeader;
import org.apache.rocketmq.remoting.protocol.header.SearchOffsetRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.SearchOffsetResponseHeader;
import org.apache.rocketmq.remoting.protocol.header.SendMessageRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.SendMessageRequestHeaderV2;
import org.apache.rocketmq.remoting.protocol.header.UnlockBatchMqRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.UpdateConsumerOffsetRequestHeader;
import org.apache.rocketmq.remoting.protocol.heartbeat.HeartbeatData;

public class MQClientAPIExt extends MQClientAPIImpl {
    private static final Logger log = LoggerFactory.getLogger(LoggerName.PROXY_LOGGER_NAME);

    private final ClientConfig clientConfig;

    private final MqClientAdminImpl mqClientAdmin;

    public MQClientAPIExt(
        ClientConfig clientConfig,
        NettyClientConfig nettyClientConfig,
        ClientRemotingProcessor clientRemotingProcessor,
        RPCHook rpcHook
    ) {
        this(clientConfig, nettyClientConfig, clientRemotingProcessor, rpcHook, null);
    }

    public MQClientAPIExt(
        ClientConfig clientConfig,
        NettyClientConfig nettyClientConfig,
        ClientRemotingProcessor clientRemotingProcessor,
        RPCHook rpcHook,
        ObjectCreator<RemotingClient> remotingClientCreator
    ) {
        super(nettyClientConfig, clientRemotingProcessor, rpcHook, clientConfig, null, remotingClientCreator);
        this.clientConfig = clientConfig;
        this.mqClientAdmin = new MqClientAdminImpl(getRemotingClient());
    }

    public boolean updateNameServerAddressList() {
        if (this.clientConfig.getNamesrvAddr() != null) {
            this.updateNameServerAddressList(this.clientConfig.getNamesrvAddr());
            log.info("user specified name server address: {}", this.clientConfig.getNamesrvAddr());
            return true;
        }
        return false;
    }

    public CompletableFuture<Void> sendHeartbeatOneway(
        String brokerAddr,
        HeartbeatData heartbeatData,
        long timeoutMillis
    ) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.HEART_BEAT, new HeartbeatRequestHeader());
            request.setLanguage(clientConfig.getLanguage());
            request.setBody(heartbeatData.encode());
            this.getRemotingClient().invokeOneway(brokerAddr, request, timeoutMillis);
            future.complete(null);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<Integer> sendHeartbeatAsync(
        String brokerAddr,
        HeartbeatData heartbeatData,
        long timeoutMillis
    ) {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.HEART_BEAT, new HeartbeatRequestHeader());
        request.setLanguage(clientConfig.getLanguage());
        request.setBody(heartbeatData.encode());

        return this.getRemotingClient().invoke(brokerAddr, request, timeoutMillis).thenCompose(response -> {
            CompletableFuture<Integer> future0 = new CompletableFuture<>();
            if (ResponseCode.SUCCESS == response.getCode()) {
                future0.complete(response.getVersion());
            } else {
                future0.completeExceptionally(new MQBrokerException(response.getCode(), response.getRemark(), brokerAddr));
            }
            return future0;
        });
    }

    public CompletableFuture<SendResult> sendMessageAsync(
        String brokerAddr,
        String brokerName,
        Message msg,
        SendMessageRequestHeader requestHeader,
        long timeoutMillis
    ) {
        SendMessageRequestHeaderV2 requestHeaderV2 = SendMessageRequestHeaderV2.createSendMessageRequestHeaderV2(requestHeader);
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE_V2, requestHeaderV2);
        request.setBody(msg.getBody());

        return this.getRemotingClient().invoke(brokerAddr, request, timeoutMillis).thenCompose(response -> {
            CompletableFuture<SendResult> future0 = new CompletableFuture<>();
            try {
                future0.complete(this.processSendResponse(brokerName, msg, response, brokerAddr));
            } catch (Exception e) {
                future0.completeExceptionally(e);
            }
            return future0;
        });
    }

    public CompletableFuture<SendResult> sendMessageAsync(
        String brokerAddr,
        String brokerName,
        List<? extends Message> msgList,
        SendMessageRequestHeader requestHeader,
        long timeoutMillis
    ) {
        SendMessageRequestHeaderV2 requestHeaderV2 = SendMessageRequestHeaderV2.createSendMessageRequestHeaderV2(requestHeader);
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.SEND_BATCH_MESSAGE, requestHeaderV2);

        CompletableFuture<SendResult> future = new CompletableFuture<>();
        try {
            requestHeader.setBatch(true);
            MessageBatch msgBatch = MessageBatch.generateFromList(msgList);
            MessageClientIDSetter.setUniqID(msgBatch);
            byte[] body = msgBatch.encode();
            msgBatch.setBody(body);

            request.setBody(body);
            return this.getRemotingClient().invoke(brokerAddr, request, timeoutMillis).thenCompose(response -> {
                CompletableFuture<SendResult> future0 = new CompletableFuture<>();
                try {
                    future0.complete(processSendResponse(brokerName, msgBatch, response, brokerAddr));
                } catch (Exception e) {
                    future0.completeExceptionally(e);
                }
                return future0;
            });
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<RemotingCommand> sendMessageBackAsync(
        String brokerAddr,
        ConsumerSendMsgBackRequestHeader requestHeader,
        long timeoutMillis
    ) {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.CONSUMER_SEND_MSG_BACK, requestHeader);
        return this.getRemotingClient().invoke(brokerAddr, request, timeoutMillis);
    }

    public CompletableFuture<PopResult> popMessageAsync(
        String brokerAddr,
        String brokerName,
        PopMessageRequestHeader requestHeader,
        long timeoutMillis
    ) {
        CompletableFuture<PopResult> future = new CompletableFuture<>();
        try {
            this.popMessageAsync(brokerName, brokerAddr, requestHeader, timeoutMillis, new PopCallback() {
                @Override
                public void onSuccess(PopResult popResult) {
                    future.complete(popResult);
                }

                @Override
                public void onException(Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<AckResult> ackMessageAsync(
        String brokerAddr,
        AckMessageRequestHeader requestHeader,
        long timeoutMillis
    ) {
        CompletableFuture<AckResult> future = new CompletableFuture<>();
        try {
            this.ackMessageAsync(brokerAddr, timeoutMillis, new AckCallback() {
                @Override
                public void onSuccess(AckResult ackResult) {
                    future.complete(ackResult);
                }

                @Override
                public void onException(Throwable t) {
                    future.completeExceptionally(t);
                }
            }, requestHeader);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<AckResult> batchAckMessageAsync(
        String brokerAddr,
        String topic,
        String consumerGroup,
        List<String> extraInfoList,
        long timeoutMillis
    ) {
        CompletableFuture<AckResult> future = new CompletableFuture<>();
        try {
            this.batchAckMessageAsync(brokerAddr, timeoutMillis, new AckCallback() {
                @Override
                public void onSuccess(AckResult ackResult) {
                    future.complete(ackResult);
                }

                @Override
                public void onException(Throwable t) {
                    future.completeExceptionally(t);
                }
            }, topic, consumerGroup, extraInfoList);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<AckResult> changeInvisibleTimeAsync(
        String brokerAddr,
        String brokerName,
        ChangeInvisibleTimeRequestHeader requestHeader,
        long timeoutMillis
    ) {
        CompletableFuture<AckResult> future = new CompletableFuture<>();
        try {
            this.changeInvisibleTimeAsync(brokerName, brokerAddr, requestHeader, timeoutMillis,
                new AckCallback() {
                    @Override
                    public void onSuccess(AckResult ackResult) {
                        future.complete(ackResult);
                    }

                    @Override
                    public void onException(Throwable t) {
                        future.completeExceptionally(t);
                    }
                }
            );
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<PullResult> pullMessageAsync(
        String brokerAddr,
        PullMessageRequestHeader requestHeader,
        long timeoutMillis
    ) {
        CompletableFuture<PullResult> future = new CompletableFuture<>();
        try {
            this.pullMessage(brokerAddr, requestHeader, timeoutMillis, CommunicationMode.ASYNC,
                new PullCallback() {
                    @Override
                    public void onSuccess(PullResult pullResult) {
                        if (pullResult instanceof PullResultExt) {
                            PullResultExt pullResultExt = (PullResultExt) pullResult;
                            if (PullStatus.FOUND.equals(pullResult.getPullStatus())) {
                                List<MessageExt> messageExtList = MessageDecoder.decodesBatch(
                                    ByteBuffer.wrap(pullResultExt.getMessageBinary()),
                                    true,
                                    false,
                                    true
                                );
                                pullResult.setMsgFoundList(messageExtList);
                            }
                        }
                        future.complete(pullResult);
                    }

                    @Override
                    public void onException(Throwable t) {
                        future.completeExceptionally(t);
                    }
                }
            );
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<Long> queryConsumerOffsetWithFuture(
        String brokerAddr,
        QueryConsumerOffsetRequestHeader requestHeader,
        long timeoutMillis
    ) {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.QUERY_CONSUMER_OFFSET, requestHeader);
        return this.getRemotingClient().invoke(brokerAddr, request, timeoutMillis).thenCompose(response -> {
            CompletableFuture<Long> future0 = new CompletableFuture<>();
            switch (response.getCode()) {
                case ResponseCode.SUCCESS: {
                    try {
                        QueryConsumerOffsetResponseHeader responseHeader =
                            (QueryConsumerOffsetResponseHeader) response.decodeCommandCustomHeader(QueryConsumerOffsetResponseHeader.class);
                        future0.complete(responseHeader.getOffset());
                    } catch (RemotingCommandException e) {
                        future0.completeExceptionally(e);
                    }
                    break;
                }
                case ResponseCode.QUERY_NOT_FOUND: {
                    future0.completeExceptionally(new OffsetNotFoundException(response.getCode(), response.getRemark(), brokerAddr));
                    break;
                }
                default: {
                    future0.completeExceptionally(new MQBrokerException(response.getCode(), response.getRemark()));
                    break;
                }
            }
            return future0;
        });
    }

    public CompletableFuture<Void> updateConsumerOffsetOneWay(
        String brokerAddr,
        UpdateConsumerOffsetRequestHeader header,
        long timeoutMillis
    ) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.UPDATE_CONSUMER_OFFSET, header);
            this.getRemotingClient().invokeOneway(brokerAddr, request, timeoutMillis);
            future.complete(null);
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<Void> updateConsumerOffsetAsync(
        String brokerAddr,
        UpdateConsumerOffsetRequestHeader header,
        long timeoutMillis
    ) {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.UPDATE_CONSUMER_OFFSET, header);
        CompletableFuture<Void> future = new CompletableFuture<>();
        invoke(brokerAddr, request, timeoutMillis).whenComplete((response, t) -> {
            if (t != null) {
                log.error("updateConsumerOffsetAsync failed, brokerAddr={}, requestHeader={}", brokerAddr, header, t);
                future.completeExceptionally(t);
                return;
            }
            switch (response.getCode()) {
                case ResponseCode.SUCCESS: {
                    future.complete(null);
                }
                case ResponseCode.SYSTEM_ERROR:
                case ResponseCode.SUBSCRIPTION_GROUP_NOT_EXIST:
                case ResponseCode.TOPIC_NOT_EXIST: {
                    future.completeExceptionally(new MQBrokerException(response.getCode(), response.getRemark()));
                }
            }
        });
        return future;
    }

    public CompletableFuture<List<String>> getConsumerListByGroupAsync(
        String brokerAddr,
        GetConsumerListByGroupRequestHeader requestHeader,
        long timeoutMillis
    ) {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.GET_CONSUMER_LIST_BY_GROUP, requestHeader);

        CompletableFuture<List<String>> future = new CompletableFuture<>();
        try {
            this.getRemotingClient().invokeAsync(brokerAddr, request, timeoutMillis, new InvokeCallback() {
                @Override
                public void operationComplete(ResponseFuture responseFuture) {

                }

                @Override
                public void operationSucceed(RemotingCommand response) {
                    switch (response.getCode()) {
                        case ResponseCode.SUCCESS: {
                            if (response.getBody() != null) {
                                GetConsumerListByGroupResponseBody body =
                                    GetConsumerListByGroupResponseBody.decode(response.getBody(), GetConsumerListByGroupResponseBody.class);
                                future.complete(body.getConsumerIdList());
                                return;
                            }
                        }
                        /*
                          @see org.apache.rocketmq.broker.processor.ConsumerManageProcessor#getConsumerListByGroup,
                         * broker will return {@link ResponseCode.SYSTEM_ERROR} if there is no consumer.
                         */
                        case ResponseCode.SYSTEM_ERROR: {
                            future.complete(Collections.emptyList());
                            return;
                        }
                        default:
                            break;
                    }
                    future.completeExceptionally(new MQBrokerException(response.getCode(), response.getRemark()));
                }

                @Override
                public void operationFail(Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<Long> getMaxOffset(String brokerAddr, GetMaxOffsetRequestHeader requestHeader,
        long timeoutMillis) {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.GET_MAX_OFFSET, requestHeader);

        CompletableFuture<Long> future = new CompletableFuture<>();
        try {
            this.getRemotingClient().invokeAsync(brokerAddr, request, timeoutMillis, new InvokeCallback() {
                @Override
                public void operationComplete(ResponseFuture responseFuture) {

                }

                @Override
                public void operationSucceed(RemotingCommand response) {
                    if (ResponseCode.SUCCESS == response.getCode()) {
                        try {
                            GetMaxOffsetResponseHeader responseHeader = (GetMaxOffsetResponseHeader) response.decodeCommandCustomHeader(GetMaxOffsetResponseHeader.class);
                            future.complete(responseHeader.getOffset());
                        } catch (Throwable t) {
                            future.completeExceptionally(t);
                        }
                    }
                    future.completeExceptionally(new MQBrokerException(response.getCode(), response.getRemark()));
                }

                @Override
                public void operationFail(Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<Long> getMinOffset(String brokerAddr, GetMinOffsetRequestHeader requestHeader,
        long timeoutMillis) {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.GET_MIN_OFFSET, requestHeader);

        CompletableFuture<Long> future = new CompletableFuture<>();
        try {
            this.getRemotingClient().invokeAsync(brokerAddr, request, timeoutMillis, new InvokeCallback() {
                @Override
                public void operationComplete(ResponseFuture responseFuture) {

                }

                @Override
                public void operationSucceed(RemotingCommand response) {
                    if (ResponseCode.SUCCESS == response.getCode()) {
                        try {
                            GetMinOffsetResponseHeader responseHeader = (GetMinOffsetResponseHeader) response.decodeCommandCustomHeader(GetMinOffsetResponseHeader.class);
                            future.complete(responseHeader.getOffset());
                        } catch (Throwable t) {
                            future.completeExceptionally(t);
                        }
                    }
                    future.completeExceptionally(new MQBrokerException(response.getCode(), response.getRemark()));
                }

                @Override
                public void operationFail(Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (Throwable t) {
            future.completeExceptionally(t);
        }
        return future;
    }

    public CompletableFuture<Long> searchOffset(String brokerAddr, SearchOffsetRequestHeader requestHeader,
        long timeoutMillis) {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.SEARCH_OFFSET_BY_TIMESTAMP, requestHeader);

        return this.getRemotingClient().invoke(brokerAddr, request, timeoutMillis).thenCompose(response -> {
            CompletableFuture<Long> future0 = new CompletableFuture<>();
            if (response.getCode() == ResponseCode.SUCCESS) {
                try {
                    SearchOffsetResponseHeader responseHeader = (SearchOffsetResponseHeader) response.decodeCommandCustomHeader(SearchOffsetResponseHeader.class);
                    future0.complete(responseHeader.getOffset());
                } catch (Throwable t) {
                    future0.completeExceptionally(t);
                }
            } else {
                future0.completeExceptionally(new MQBrokerException(response.getCode(), response.getRemark()));
            }
            return future0;
        });
    }

    public CompletableFuture<Set<MessageQueue>> lockBatchMQWithFuture(String brokerAddr,
        LockBatchRequestBody requestBody, long timeoutMillis) {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.LOCK_BATCH_MQ, new LockBatchMqRequestHeader());
        request.setBody(requestBody.encode());
        return this.getRemotingClient().invoke(brokerAddr, request, timeoutMillis).thenCompose(response -> {
            CompletableFuture<Set<MessageQueue>> future0 = new CompletableFuture<>();
            if (response.getCode() == ResponseCode.SUCCESS) {
                try {
                    LockBatchResponseBody responseBody = LockBatchResponseBody.decode(response.getBody(), LockBatchResponseBody.class);
                    Set<MessageQueue> messageQueues = responseBody.getLockOKMQSet();
                    future0.complete(messageQueues);
                } catch (Throwable t) {
                    future0.completeExceptionally(t);
                }
            } else {
                future0.completeExceptionally(new MQBrokerException(response.getCode(), response.getRemark()));
            }
            return future0;
        });
    }

    public CompletableFuture<Void> unlockBatchMQOneway(String brokerAddr,
        UnlockBatchRequestBody requestBody, long timeoutMillis) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.UNLOCK_BATCH_MQ, new UnlockBatchMqRequestHeader());
        request.setBody(requestBody.encode());
        try {
            this.getRemotingClient().invokeOneway(brokerAddr, request, timeoutMillis);
            future.complete(null);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public CompletableFuture<Boolean> notification(String brokerAddr, NotificationRequestHeader requestHeader,
        long timeoutMillis) {
        return notificationWithPollingStats(brokerAddr, requestHeader, timeoutMillis).thenApply(NotifyResult::isHasMsg);
    }

    public CompletableFuture<NotifyResult> notificationWithPollingStats(String brokerAddr,
        NotificationRequestHeader requestHeader,
        long timeoutMillis) {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.NOTIFICATION, requestHeader);
        return this.getRemotingClient().invoke(brokerAddr, request, timeoutMillis).thenCompose(response -> {
            CompletableFuture<NotifyResult> future0 = new CompletableFuture<>();
            if (response.getCode() == ResponseCode.SUCCESS) {
                try {
                    NotificationResponseHeader responseHeader = (NotificationResponseHeader) response.decodeCommandCustomHeader(NotificationResponseHeader.class);
                    NotifyResult notifyResult = new NotifyResult();
                    notifyResult.setHasMsg(responseHeader.isHasMsg());
                    notifyResult.setPollingFull(responseHeader.isPollingFull());
                    future0.complete(notifyResult);
                } catch (Throwable t) {
                    future0.completeExceptionally(t);
                }
            } else {
                future0.completeExceptionally(new MQBrokerException(response.getCode(), response.getRemark()));
            }
            return future0;
        });
    }

    public CompletableFuture<String> recallMessageAsync(String brokerAddr,
        RecallMessageRequestHeader requestHeader, long timeoutMillis) {
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.RECALL_MESSAGE, requestHeader);
        return this.getRemotingClient().invoke(brokerAddr, request, timeoutMillis).thenCompose(response -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            if (ResponseCode.SUCCESS == response.getCode()) {
                try {
                    RecallMessageResponseHeader responseHeader =
                        response.decodeCommandCustomHeader(RecallMessageResponseHeader.class);
                    future.complete(responseHeader.getMsgId());
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            } else {
                future.completeExceptionally(new MQBrokerException(response.getCode(), response.getRemark(), brokerAddr));
            }
            return future;
        });
    }

    public CompletableFuture<RemotingCommand> invoke(String brokerAddr, RemotingCommand request, long timeoutMillis) {
        return getRemotingClient().invoke(brokerAddr, request, timeoutMillis);
    }

    public CompletableFuture<Void> invokeOneway(String brokerAddr, RemotingCommand request, long timeoutMillis) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            this.getRemotingClient().invokeOneway(brokerAddr, request, timeoutMillis);
            future.complete(null);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public MqClientAdminImpl getMqClientAdmin() {
        return mqClientAdmin;
    }
}
