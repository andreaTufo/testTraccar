package org.traccar.handler;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.netty.channel.ChannelHandler;
import org.traccar.BaseDataHandler;
import org.traccar.Context;
import org.traccar.database.IdentityManager;
import org.traccar.model.Position;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@ChannelHandler.Sharable
public class zDataHandler extends BaseDataHandler {

    private static final String EXCHANGE_NAME = "amq.topic";

    private IdentityManager identityManager;
    public zDataHandler(IdentityManager im){

        this.identityManager = im;
    }

    private String buildPayload(Double lat, Double lon, Double alt, Double speed, Map<String, Object> attr){

        boolean endPay = false;
        StringBuilder payload = new StringBuilder();
        payload.append("{\"lat\":").append(lat.toString()).
                append(",\"lon\":").append(lon.toString()).
                append(",\"alt\":").append(alt.toString()).
                append(",\"speed\":").append(speed.toString());

        if(attr.isEmpty() || attr == null){
            payload.append("}");
            endPay = true;
        }
        else{
            for(String keys : attr.keySet()){
                payload.append(",\"").append(keys).append("\":");
                payload.append(attr.get(keys));
            }
        }
        if(!endPay){
            payload.append("}");
        }

        System.out.println(payload.toString());
        return payload.toString();
    }


    protected void publishRMQ(String devId, Position p){

        System.out.println("Publishing on rmq code");
        String payload = buildPayload(p.getLatitude(), p.getLongitude(), p.getAltitude(), p.getSpeed(), p.getAttributes());
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("rmq");
        factory.setUsername("admin");
        factory.setPassword("Z3rynthT3st");
        factory.setVirtualHost("iot");
        try (Connection connection = factory.newConnection();
             com.rabbitmq.client.Channel channel = connection.createChannel()) {
            channel.exchangeDeclare(EXCHANGE_NAME, "topic", true);

            channel.basicPublish(EXCHANGE_NAME, "j.data."+devId+".latlon", null, payload.getBytes(StandardCharsets.UTF_8));

        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected Position handlePosition(Position position) {

        String uniqueId = identityManager.getById(position.getId()).getUniqueId();
        publishRMQ(uniqueId, position);

        return position;
    }
}
