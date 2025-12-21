package com.fongmi.android.tv.utils;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RtspDurationParser {
    // RTSP默认端口
    private static final int RTSP_PORT = 554;
    // 匹配SDP中的duration字段（d=0或d=3600.000等）
    private static final Pattern SDP_DURATION_PATTERN = Pattern.compile("d=(\\d+(?:\\.\\d+)?)");
    // 匹配Range响应头中的总时长（npt=0.000-3600.000）
    private static final Pattern RANGE_DURATION_PATTERN = Pattern.compile("npt=\\d+(?:\\.\\d+)?-(\\d+(?:\\.\\d+)?)");
    // 匹配RTSP重定向状态码（301/302）
    private static final Pattern RTSP_REDIRECT_STATUS_PATTERN = Pattern.compile("RTSP/1\\.0 (30[12])");
    // 匹配Location头中的新RTSP URL
    private static final Pattern RTSP_LOCATION_PATTERN = Pattern.compile("Location: (rtsp://.*)");
    // 最大重定向次数（避免无限循环）
    private static final int MAX_REDIRECTS = 3;
    // Socket读取超时（毫秒）
    private static final int SOCKET_TIMEOUT = 5000;

    /**
     * 递归获取RTSP URL的片长（处理重定向）
     * @param rtspUrl RTSP点播地址
     * @param redirectCount 当前重定向次数
     * @return 片长（秒），返回-1表示解析失败/超时/重定向超限
     */
    private static double getRtspDurationWithRedirect(String rtspUrl, int redirectCount) {
        // 重定向次数超限，终止
        if (redirectCount > MAX_REDIRECTS) {
            System.err.println("重定向次数超过上限（" + MAX_REDIRECTS + "次），终止请求");
            return -1.0;
        }

        if (rtspUrl == null || !rtspUrl.startsWith("rtsp://")) {
            throw new IllegalArgumentException("无效的RTSP URL: " + rtspUrl);
        }

        // 解析RTSP URL的主机和路径
        String host = rtspUrl.split("//")[1].split("/")[0];
        // 处理带端口的host（如rtsp://192.168.1.1:555/stream.mp4）
        int port = RTSP_PORT;
        if (host.contains(":")) {
            String[] hostPort = host.split(":");
            host = hostPort[0];
            port = Integer.parseInt(hostPort[1]);
        }

        Socket socket = null;
        OutputStreamWriter writer = null;
        BufferedReader reader = null;

        try {
            // 1. 建立TCP连接 + 设置超时
            socket = new Socket(host, port);
            socket.setSoTimeout(SOCKET_TIMEOUT); // 关键：读取超时
            // 指定UTF-8编码，避免乱码
            writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            // 2. 发送RTSP DESCRIBE请求（RTSP 1.0）
            String cseq = String.valueOf(redirectCount + 1); // 序列号递增
            String describeRequest = String.format(
                    "DESCRIBE %s RTSP/1.0\r\n" +
                            "CSeq: %s\r\n" +
                            "Accept: application/sdp\r\n" +
                            "User-Agent: Java-RTSP-Client\r\n\r\n",
                    rtspUrl, cseq
            );
            writer.write(describeRequest);
            writer.flush();

            // 3. 读取响应并解析
            StringBuilder response = new StringBuilder();
            String line;
            int maxLines = 100; // 限制最大读取行数，避免无限循环
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < maxLines) {
                response.append(line).append("\n");
                lineCount++;
                // 响应结束标记（空行），停止读取
                if (line.contains("range") || line.contains("Location")) {
                    break;
                }
            }
            String responseStr = response.toString();
            System.out.println("RTSP响应（第" + redirectCount + "次请求）:\n" + responseStr);

            // 4. 检查是否为重定向响应
            Matcher statusMatcher = RTSP_REDIRECT_STATUS_PATTERN.matcher(responseStr);
            if (statusMatcher.find()) {
                String statusCode = statusMatcher.group(1);
                System.out.println("检测到重定向状态码：" + statusCode + "，解析新URL...");

                // 提取Location头中的新RTSP URL
                Matcher locationMatcher = RTSP_LOCATION_PATTERN.matcher(responseStr);
                if (locationMatcher.find()) {
                    String newRtspUrl = locationMatcher.group(1).trim();
                    System.out.println("重定向到新URL：" + newRtspUrl);
                    // 递归请求新URL，重定向次数+1
                    return getRtspDurationWithRedirect(newRtspUrl, redirectCount + 1);
                } else {
                    System.err.println("重定向响应中未找到Location头");
                    return -1.0;
                }
            }

            // 5. 非重定向响应，解析时长
            // 优先解析SDP中的duration字段
//            Matcher sdpMatcher = SDP_DURATION_PATTERN.matcher(responseStr);
//            if (sdpMatcher.find()) {
//                return Double.parseDouble(sdpMatcher.group(1));
//            }

            // 备用：解析Range响应头中的总时长
            Matcher rangeMatcher = RANGE_DURATION_PATTERN.matcher(responseStr);
            if (rangeMatcher.find()) {
                return Double.parseDouble(rangeMatcher.group(1));
            }

            System.err.println("响应中未找到时长信息");
            return -1.0;

        } catch (SocketTimeoutException e) {
            System.err.println("读取RTSP响应超时（" + SOCKET_TIMEOUT + "ms）: " + rtspUrl);
            return -1.0;
        } catch (IOException | NumberFormatException e) {
            System.err.println("请求RTSP URL失败: " + rtspUrl);
            e.printStackTrace();
            return -1.0;
        } finally {
            // 确保资源关闭，避免端口泄露
            try {
                if (reader != null) reader.close();
                if (writer != null) writer.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 对外暴露的入口方法（默认重定向次数为0）
     * @param rtspUrl RTSP点播地址
     * @return 片长（秒）
     */
    public static double getRtspDuration(String rtspUrl) {
        return getRtspDurationWithRedirect(rtspUrl, 0);
    }

    public static void main(String[] args) {
        // 强制设置控制台输出编码为UTF-8（兼容Windows/Linux）
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
            System.setErr(new PrintStream(System.err, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        // 测试示例（替换为实际RTSP URL，支持重定向）
        String rtspUrl = "rtsp://10.1.64.176:1554/iptv/vod/iptv/001/001/1783/00000050280001364658.mpg"; // 原URL（会重定向）
        double duration = getRtspDuration(rtspUrl);
        if (duration > 0) {
            System.out.printf("最终片长：%.3f秒（%d分%.2f秒）%n",
                    duration, (int) duration / 60, duration % 60);
        } else {
            System.out.println("无法获取片长信息（超时/重定向失败/无时长字段）");
        }
    }
}