package com.alibaba.otter.canal.parse.inbound.mysql.utils;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.otter.canal.parse.inbound.mysql.networking.packets.HeaderPacket;
import com.alibaba.otter.canal.parse.inbound.mysql.networking.packets.client.QueryCommandPacket;
import com.alibaba.otter.canal.parse.inbound.mysql.networking.packets.server.FieldPacket;
import com.alibaba.otter.canal.parse.inbound.mysql.networking.packets.server.ResultSetHeaderPacket;
import com.alibaba.otter.canal.parse.inbound.mysql.networking.packets.server.ResultSetPacket;
import com.alibaba.otter.canal.parse.inbound.mysql.networking.packets.server.RowDataPacket;
import com.alibaba.otter.canal.parse.support.PacketManager;

public class MysqlQueryExecutor {

    private SocketChannel channel;

    public MysqlQueryExecutor(SocketChannel ch){
        this.channel = ch;
    }

    /**
     * (Result Set Header Packet) the number of columns <br>
     * (Field Packets) column descriptors <br>
     * (EOF Packet) marker: end of Field Packets <br>
     * (Row Data Packets) row contents <br>
     * (EOF Packet) marker: end of Data Packets
     * 
     * @param queryString
     * @return
     * @throws IOException
     */
    public ResultSetPacket query(String queryString) throws IOException {
        QueryCommandPacket cmd = new QueryCommandPacket();
        cmd.setQueryString(queryString);
        byte[] bodyBytes = cmd.toBytes();
        PacketManager.write(channel, bodyBytes);

        ResultSetHeaderPacket rsHeader = new ResultSetHeaderPacket();
        rsHeader.fromBytes(readNextPacket());

        List<FieldPacket> fields = new ArrayList<FieldPacket>();
        for (int i = 0; i < rsHeader.getColumnCount(); i++) {
            FieldPacket fp = new FieldPacket();
            fp.fromBytes(readNextPacket());
            fields.add(fp);
        }

        readEofPacket();

        List<RowDataPacket> rowData = new ArrayList<RowDataPacket>();
        while (true) {
            byte[] body = readNextPacket();
            if (body[0] == -2) {
                break;
            }
            RowDataPacket rowDataPacket = new RowDataPacket();
            rowDataPacket.fromBytes(body);
            rowData.add(rowDataPacket);
        }

        ResultSetPacket resultSet = new ResultSetPacket();
        resultSet.getFieldDescriptors().addAll(fields);
        for (RowDataPacket r : rowData) {
            resultSet.getFieldValues().addAll(r.getColumns());
        }
        resultSet.setSourceAddress(channel.socket().getRemoteSocketAddress());

        return resultSet;
    }

    private void readEofPacket() throws IOException {
        byte[] eofBody = readNextPacket();
        if (eofBody[0] != -2) {
            throw new IOException("EOF Packet is expected, but packet with field_count=" + eofBody[0] + " is found.");
        }
    }

    protected byte[] readNextPacket() throws IOException {
        HeaderPacket h = PacketManager.readHeader(channel, 4);
        return PacketManager.readBytes(channel, h.getPacketBodyLength());
    }
}