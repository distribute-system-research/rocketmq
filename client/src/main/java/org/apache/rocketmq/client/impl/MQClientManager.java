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
package org.apache.rocketmq.client.impl;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.rocketmq.client.ClientConfig;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.producer.ProduceAccumulator;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.logging.org.slf4j.Logger;
import org.apache.rocketmq.logging.org.slf4j.LoggerFactory;

public class MQClientManager {
    private final static Logger log = LoggerFactory.getLogger(MQClientManager.class);
    private static MQClientManager instance = new MQClientManager();
    private AtomicInteger factoryIndexGenerator = new AtomicInteger();
    private ConcurrentMap<String/* clientId */, MQClientInstance> factoryTable =
        new ConcurrentHashMap<>();
    private ConcurrentMap<String/* clientId */, ProduceAccumulator> accumulatorTable =
        new ConcurrentHashMap<String, ProduceAccumulator>();


    private MQClientManager() {

    }

    public static MQClientManager getInstance() {
        return instance;
    }

    public MQClientInstance getOrCreateMQClientInstance(final ClientConfig clientConfig) {
        return getOrCreateMQClientInstance(clientConfig, null);
    }
    public MQClientInstance getOrCreateMQClientInstance(final ClientConfig clientConfig, RPCHook rpcHook) {
        String clientId = clientConfig.buildMQClientId();
        MQClientInstance instance = this.factoryTable.get(clientId);
        if (null == instance) {
            instance =
                new MQClientInstance(clientConfig.cloneClientConfig(),
                    this.factoryIndexGenerator.getAndIncrement(), clientId, rpcHook);
            MQClientInstance prev = this.factoryTable.putIfAbsent(clientId, instance);
            if (prev != null) {
                instance = prev;
                log.warn("Returned Previous MQClientInstance for clientId:[{}]", clientId);
            } else {
                log.info("Created new MQClientInstance for clientId:[{}]", clientId);
            }
        }

        return instance;
    }
    public ProduceAccumulator getOrCreateProduceAccumulator(final ClientConfig clientConfig) {
        String clientId = clientConfig.buildMQClientId();
        ProduceAccumulator accumulator = this.accumulatorTable.get(clientId);
        if (null == accumulator) {
            accumulator = new ProduceAccumulator(clientId);
            ProduceAccumulator prev = this.accumulatorTable.putIfAbsent(clientId, accumulator);
            if (prev != null) {
                accumulator = prev;
                log.warn("Returned Previous ProduceAccumulator for clientId:[{}]", clientId);
            } else {
                log.info("Created new ProduceAccumulator for clientId:[{}]", clientId);
            }
        }

        return accumulator;
    }

    public void removeClientFactory(final String clientId) {
        this.factoryTable.remove(clientId);
    }

    public ConcurrentMap<String, MQClientInstance> getFactoryTable() {
        return factoryTable;
    }
}
