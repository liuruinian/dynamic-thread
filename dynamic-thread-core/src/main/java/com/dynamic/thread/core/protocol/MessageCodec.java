package com.dynamic.thread.core.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Message codec for encoding and decoding messages.
 * 
 * Protocol format:
 * +--------+--------+----------+------+
 * | Magic  | Type   | Length   | Body |
 * | 4bytes | 1byte  | 4bytes   | JSON |
 * +--------+--------+----------+------+
 */
@Slf4j
public class MessageCodec extends ByteToMessageCodec<Message> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
        // Write magic number
        out.writeInt(Message.MAGIC_NUMBER);

        // Write message type
        out.writeByte(msg.getType().getCode());

        // Serialize message body to JSON
        byte[] bodyBytes = serializeMessage(msg);

        // Write body length
        out.writeInt(bodyBytes.length);

        // Write body
        out.writeBytes(bodyBytes);

        log.debug("Encoded message: type={}, length={}", msg.getType(), bodyBytes.length);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // Check if we have enough bytes for header (magic + type + length = 9 bytes)
        if (in.readableBytes() < 9) {
            return;
        }

        in.markReaderIndex();

        // Read and validate magic number
        int magic = in.readInt();
        if (magic != Message.MAGIC_NUMBER) {
            log.error("Invalid magic number: {}, expected: {}", magic, Message.MAGIC_NUMBER);
            in.resetReaderIndex();
            throw new IllegalStateException("Invalid magic number");
        }

        // Read message type
        byte typeCode = in.readByte();
        MessageType type = MessageType.fromCode(typeCode);

        // Read body length
        int length = in.readInt();

        // Check if we have enough bytes for the body
        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        // Read body
        byte[] bodyBytes = new byte[length];
        in.readBytes(bodyBytes);

        // Deserialize message
        Message message = deserializeMessage(bodyBytes, type);
        out.add(message);

        log.debug("Decoded message: type={}, length={}", type, length);
    }

    private byte[] serializeMessage(Message message) throws JsonProcessingException {
        return OBJECT_MAPPER.writeValueAsBytes(message);
    }

    private Message deserializeMessage(byte[] bytes, MessageType expectedType) throws Exception {
        Message message = OBJECT_MAPPER.readValue(bytes, Message.class);
        if (message.getType() == null) {
            message.setType(expectedType);
        }
        return message;
    }
}
