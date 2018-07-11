package com.ctrip.xpipe.redis.keeper.impl;

import com.ctrip.xpipe.api.lifecycle.ComponentRegistry;
import com.ctrip.xpipe.lifecycle.CreatedComponentRedistry;
import com.ctrip.xpipe.redis.core.proxy.DefaultProxyProtocolParser;
import com.ctrip.xpipe.redis.core.proxy.ProxyProtocol;
import com.ctrip.xpipe.redis.core.proxy.endpoint.ProxyEnabledEndpoint;
import com.ctrip.xpipe.redis.core.proxy.endpoint.ProxyEndpointManager;
import com.ctrip.xpipe.redis.core.store.MetaStore;
import com.ctrip.xpipe.redis.core.store.ReplicationStore;
import com.ctrip.xpipe.redis.keeper.AbstractRedisKeeperTest;
import com.ctrip.xpipe.redis.keeper.RedisKeeperServer;
import com.ctrip.xpipe.redis.keeper.RedisMaster;
import com.ctrip.xpipe.redis.keeper.container.ComponentRegistryHolder;
import com.ctrip.xpipe.simpleserver.Server;
import com.google.common.collect.Lists;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;

import static com.ctrip.xpipe.redis.keeper.impl.AbstractRedisMasterReplication.KEY_MASTER_CONNECT_RETRY_DELAY_SECONDS;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * @author chen.zhu
 * <p>
 * Jul 11, 2018
 */

@RunWith(MockitoJUnitRunner.class)
public class AbstractRedisMasterReplicationTest extends AbstractRedisKeeperTest {

    @Mock
    private RedisMaster redisMaster;

    @Mock
    private RedisKeeperServer redisKeeperServer;

    @Mock
    private ReplicationStore replicationStore;

    @Mock
    private MetaStore metaStore;

    private NioEventLoopGroup nioEventLoopGroup;

    private AbstractRedisMasterReplication redisMasterReplication;

    private int replTimeoutMilli = 6000;

    @Before
    public void beforeDefaultRedisMasterReplicationTest() throws Exception {

        MockitoAnnotations.initMocks(this);

        nioEventLoopGroup = new NioEventLoopGroup();

        redisMasterReplication = new DefaultRedisMasterReplication(redisMaster, redisKeeperServer, nioEventLoopGroup,
                scheduled, replTimeoutMilli);
        when(redisKeeperServer.getRedisKeeperServerState()).thenReturn(new RedisKeeperServerStateActive(redisKeeperServer));

        when(redisMaster.getCurrentReplicationStore()).thenReturn(replicationStore);
        when(replicationStore.getMetaStore()).thenReturn(metaStore);
    }

    @Test
    public void testListeningPortCommandTimeOut() throws Exception {
        System.setProperty(KEY_MASTER_CONNECT_RETRY_DELAY_SECONDS, "0");
        Server server = startEmptyServer();
        ProxyProtocol protocol = new DefaultProxyProtocolParser().read("PROXY ROUTE TCP://127.0.0.1:"+server.getPort());
        ProxyEnabledEndpoint endpoint = new ProxyEnabledEndpoint("127.0.0.1", server.getPort(), protocol);

        when(redisMaster.masterEndPoint()).thenReturn(endpoint);
        redisMasterReplication = new DefaultRedisMasterReplication(redisMaster, redisKeeperServer, nioEventLoopGroup, scheduled, replTimeoutMilli);

        ProxyEndpointManager proxyEndpointManager = mock(ProxyEndpointManager.class);
        when(proxyEndpointManager.getAvailableProxyEndpoints()).thenReturn(protocol.nextEndpoints());

        ComponentRegistry registry = new CreatedComponentRedistry();
        registry.initialize();
        registry.start();
        registry.add(proxyEndpointManager);
        ComponentRegistryHolder.initializeRegistry(registry);

        redisMasterReplication = spy(redisMasterReplication);
        redisMasterReplication.setCommandTimeoutMilli(5);
        redisMasterReplication.initialize();
        redisMasterReplication.start();


        verify(redisMasterReplication, timeout(500).atLeast(2)).connectWithMaster();
    }
}