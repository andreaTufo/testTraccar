/*
 * Copyright 2012 - 2020 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.api.BaseObjectResource;
import org.traccar.api.UserPrincipal;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.database.CommandsManager;
import org.traccar.database.ConnectionManager;
import org.traccar.database.IdentityManager;
import org.traccar.database.StatisticsManager;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Command;
import org.traccar.model.Device;
import org.traccar.model.Position;

import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeoutException;

public abstract class BaseProtocolDecoder extends ExtendedObjectDecoder {


    private static final Logger LOGGER = LoggerFactory.getLogger(BaseProtocolDecoder.class);

    private static final String PROTOCOL_UNKNOWN = "unknown";

    private final Config config = Context.getConfig();
    private final IdentityManager identityManager = Context.getIdentityManager();
    private final ConnectionManager connectionManager = Context.getConnectionManager();
    private final StatisticsManager statisticsManager;
    private final Protocol protocol;

    public BaseProtocolDecoder(Protocol protocol) {
        this.protocol = protocol;
        statisticsManager = Main.getInjector() != null ? Main.getInjector().getInstance(StatisticsManager.class) : null;
    }

    public String getProtocolName() {
        return protocol != null ? protocol.getName() : PROTOCOL_UNKNOWN;
    }

    public String getServer(Channel channel, char delimiter) {
        String server = config.getString(Keys.PROTOCOL_SERVER.withPrefix(getProtocolName()));
        if (server == null && channel != null) {
            InetSocketAddress address = (InetSocketAddress) channel.localAddress();
            server = address.getAddress().getHostAddress() + ":" + address.getPort();
        }
        return server != null ? server.replace(':', delimiter) : null;
    }

    protected double convertSpeed(double value, String defaultUnits) {
        switch (config.getString(getProtocolName() + ".speed", defaultUnits)) {
            case "kmh":
                return UnitsConverter.knotsFromKph(value);
            case "mps":
                return UnitsConverter.knotsFromMps(value);
            case "mph":
                return UnitsConverter.knotsFromMph(value);
            case "kn":
            default:
                return value;
        }
    }

    protected TimeZone getTimeZone(long deviceId) {
        return getTimeZone(deviceId, "UTC");
    }

    protected TimeZone getTimeZone(long deviceId, String defaultTimeZone) {
        TimeZone result = TimeZone.getTimeZone(defaultTimeZone);
        String timeZoneName = identityManager.lookupAttributeString(deviceId, "decoder.timezone", null, false, true);
        if (timeZoneName != null) {
            result = TimeZone.getTimeZone(timeZoneName);
        }
        return result;
    }

    private DeviceSession channelDeviceSession; // connection-based protocols
    private final Map<SocketAddress, DeviceSession> addressDeviceSessions = new HashMap<>(); // connectionless protocols


    public boolean authAuthz(Object msg, String uniqueId) throws IOException, InterruptedException {


        FullHttpRequest request = (FullHttpRequest) msg;

        QueryStringDecoder decoder = new QueryStringDecoder(request.content().toString(StandardCharsets.US_ASCII), false);
        String cert = decoder.toString();

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest requestAuth = HttpRequest.newBuilder()
                .uri(URI.create("http://dev-authentication:5000/v1/auth"))
                .POST(HttpRequest.BodyPublishers.ofString(cert))
                .build();

        HttpResponse<String> responseAuth = client.send(requestAuth,
                HttpResponse.BodyHandlers.ofString());


        HttpRequest requestAuthor = HttpRequest.newBuilder()
                .uri(URI.create("http://dev-authorization:5000/v1/authz/resource?username="+uniqueId+"&vhost=iot&resource=exchange&name=amq.topic&permission=write"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> responseAuthor = client.send(requestAuthor,
                HttpResponse.BodyHandlers.ofString());


        //topic auth
        HttpRequest requestAuthor1 = HttpRequest.newBuilder()
                .uri(URI.create("http://dev-authorization:5000/v1/authz/topic?username="+uniqueId+"&vhost=iot&resource=topic&name=amq.topic&permission=write&routingkey=j.data."+uniqueId+".latlon"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> responseAuthor1 = client.send(requestAuthor1,
                HttpResponse.BodyHandlers.ofString());


        String pass = cert.split("\"password\":\"")[1].split("\"")[0];
        HttpRequest requestAuthor2 = HttpRequest.newBuilder()
                .uri(URI.create("http://dev-authorization:5000/v1/authz/user?username="+uniqueId+"&password="+pass))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> responseAuthor2 = client.send(requestAuthor2,
                HttpResponse.BodyHandlers.ofString());

        HttpRequest requestAuthor3 = HttpRequest.newBuilder()
                .uri(URI.create("http://dev-authorization:5000/v1/authz/vhost?username="+uniqueId+"&vhost=iot&ip=127.0.0.1"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> responseAuthor3 = client.send(requestAuthor3,
                HttpResponse.BodyHandlers.ofString());

        return (responseAuthor2.statusCode() == HttpResponseStatus.OK.code()) && (responseAuthor1.statusCode() == HttpResponseStatus.OK.code())
                    && (responseAuthor.statusCode() == HttpResponseStatus.OK.code()) && (responseAuthor3.statusCode() == HttpResponseStatus.OK.code())
                                && (responseAuth.statusCode() == HttpResponseStatus.OK.code());
    }


    private long findDeviceId(SocketAddress remoteAddress, String... uniqueIds) {
       // String message = "{\"latitude\":" + uniqueIds[1] +",\"longitude\":" +uniqueIds[2] + "}";

        if (uniqueIds.length > 0) {
            long deviceId = 0;
            Device device = null;
            try {
                for (String uniqueId : uniqueIds) {
                    if (uniqueId != null) {
                        device = identityManager.getByUniqueId(uniqueId);
                        if (device != null) {
                            deviceId = device.getId();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Find device error", e);
            }
            if (deviceId == 0 && config.getBoolean(Keys.DATABASE_REGISTER_UNKNOWN)) {
                return identityManager.addUnknownDevice(uniqueIds[0]);
            }
            if (device != null && !device.getDisabled()) {

                return deviceId;
            }
            StringBuilder message = new StringBuilder();
            if (deviceId == 0) {
                message.append("Unknown device -");
            } else {
                message.append("Disabled device -");
            }
            for (String uniqueId : uniqueIds) {
                message.append(" ").append(uniqueId);
            }
            if (remoteAddress != null) {
                message.append(" (").append(((InetSocketAddress) remoteAddress).getHostString()).append(")");
            }
            LOGGER.warn(message.toString());
        }
        return 0;
    }

    public DeviceSession getDeviceSession(Channel channel, SocketAddress remoteAddress, String... uniqueIds) {
        return getDeviceSession(channel, remoteAddress, false, uniqueIds);
    }

    public DeviceSession getDeviceSession(
            Channel channel, SocketAddress remoteAddress, boolean ignoreCache, String... uniqueIds) {
        if (channel != null && BasePipelineFactory.getHandler(channel.pipeline(), HttpRequestDecoder.class) != null
                || ignoreCache || config.getBoolean(Keys.PROTOCOL_IGNORE_SESSIONS_CACHE.withPrefix(getProtocolName()))
                || config.getBoolean(Keys.DECODER_IGNORE_SESSIONS_CACHE)) {



            long deviceId = findDeviceId(remoteAddress, uniqueIds);
            if (deviceId != 0) {
                if (connectionManager != null) {
                    connectionManager.addActiveDevice(deviceId, protocol, channel, remoteAddress);
                }
                return new DeviceSession(deviceId);
            } else {
                return null;
            }
        }
        if (channel instanceof DatagramChannel) {
            long deviceId = findDeviceId(remoteAddress, uniqueIds);
            DeviceSession deviceSession = addressDeviceSessions.get(remoteAddress);
            if (deviceSession != null && (deviceSession.getDeviceId() == deviceId || uniqueIds.length == 0)) {
                return deviceSession;
            } else if (deviceId != 0) {
                deviceSession = new DeviceSession(deviceId);
                addressDeviceSessions.put(remoteAddress, deviceSession);
                if (connectionManager != null) {
                    connectionManager.addActiveDevice(deviceId, protocol, channel, remoteAddress);
                }
                return deviceSession;
            } else {
                return null;
            }
        } else {
            if (channelDeviceSession == null) {
                long deviceId = findDeviceId(remoteAddress, uniqueIds);
                if (deviceId != 0) {
                    channelDeviceSession = new DeviceSession(deviceId);
                    if (connectionManager != null) {
                        connectionManager.addActiveDevice(deviceId, protocol, channel, remoteAddress);
                    }
                }
            }
            return channelDeviceSession;
        }
    }

    public void getLastLocation(Position position, Date deviceTime) {
        if (position.getDeviceId() != 0) {
            position.setOutdated(true);

            Position last = identityManager.getLastPosition(position.getDeviceId());
            if (last != null) {
                position.setFixTime(last.getFixTime());
                position.setValid(last.getValid());
                position.setLatitude(last.getLatitude());
                position.setLongitude(last.getLongitude());
                position.setAltitude(last.getAltitude());
                position.setSpeed(last.getSpeed());
                position.setCourse(last.getCourse());
                position.setAccuracy(last.getAccuracy());
            } else {
                position.setFixTime(new Date(0));
            }

            if (deviceTime != null) {
                position.setDeviceTime(deviceTime);
            } else {
                position.setDeviceTime(new Date());
            }
        }
    }

    @Override
    protected void onMessageEvent(
            Channel channel, SocketAddress remoteAddress, Object originalMessage, Object decodedMessage) {
        if (statisticsManager != null) {
            statisticsManager.registerMessageReceived();
        }
        Set<Long> deviceIds = new HashSet<>();
        if (decodedMessage != null) {
            if (decodedMessage instanceof Position) {
                deviceIds.add(((Position) decodedMessage).getDeviceId());
            } else if (decodedMessage instanceof Collection) {
                Collection<Position> positions = (Collection) decodedMessage;
                for (Position position : positions) {
                    deviceIds.add(position.getDeviceId());
                }
            }
        }
        if (deviceIds.isEmpty()) {
            DeviceSession deviceSession = getDeviceSession(channel, remoteAddress);
            if (deviceSession != null) {
                deviceIds.add(deviceSession.getDeviceId());
            }
        }
        for (long deviceId : deviceIds) {
            connectionManager.updateDevice(deviceId, Device.STATUS_ONLINE, new Date());
            sendQueuedCommands(channel, remoteAddress, deviceId);
        }
    }

    protected void sendQueuedCommands(Channel channel, SocketAddress remoteAddress, long deviceId) {
        CommandsManager commandsManager = Context.getCommandsManager();
        if (commandsManager != null) {
            for (Command command : commandsManager.readQueuedCommands(deviceId)) {
                protocol.sendDataCommand(channel, remoteAddress, command);
            }
        }
    }

    @Override
    protected Object handleEmptyMessage(Channel channel, SocketAddress remoteAddress, Object msg) {
        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress);
        if (config.getBoolean(Keys.DATABASE_SAVE_EMPTY) && deviceSession != null) {
            Position position = new Position(getProtocolName());
            position.setDeviceId(deviceSession.getDeviceId());
            getLastLocation(position, null);
            return position;
        } else {
            return null;
        }
    }

}
