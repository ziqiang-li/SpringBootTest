/*
用于监听Spring Boot的请求和返回流。注：请求的Body体如果不是Json会报错。
需要增加依赖
		<dependency>
			<groupId>org.slf4j</groupId>
			<artifactId>slf4j-simple</artifactId>
			<version>1.7.25</version>
			<scope>compile</scope>
		</dependency>

		<dependency>
			<groupId>com.alibaba</groupId>
			<artifactId>fastjson</artifactId>
			<version>1.2.76</version>
		</dependency>

		<dependency>
			<groupId>cn.hutool</groupId>
			<artifactId>hutool-core</artifactId>
			<version>4.0.1</version>
		</dependency>
		<dependency>
			<groupId>org.projectlombok</groupId>
			<artifactId>lombok</artifactId>
			<version>1.16.10</version>
			<scope>provided</scope>
		</dependency>
配置 application.yaml
logging:
  level:
    com.example.demo: debug
  enable-api-log: true

 */
package com.example.demo.filter;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 请求日志输出
 */
@Slf4j
@Order(1)
@ConditionalOnExpression("'${logging.enable-api-log}'!='null' && '${logging.enable-api-log}'=='true'")// 开启日志时当前过滤器才会生效
@Component
@WebFilter(filterName = "webLogFilter", urlPatterns = "/*")
public class WebLogFilter implements Filter {
    TimeInterval timeInterval = new TimeInterval();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("open web-api log");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        StringBuffer sb = new StringBuffer();
        //不输出非json请求,非json存在问题暂时不处理
        String requestURI = ((HttpServletRequest) request).getRequestURI();
        // 包装request和response
        HttpServletRequest requestSer = (HttpServletRequest) request;
        int contentLength = request.getContentLength();
        if (contentLength <= 0) {//异常请求直接放行
            sb.append("\n\nURL :" + ((HttpServletRequest) request).getRequestURL() +" " + ((HttpServletRequest) request).getMethod()+" ");
            log.debug(sb.toString());
            chain.doFilter(request, response);
            return;
        }
        RequestWrapper requestWrapper = new RequestWrapper(requestSer);
        ResponseWrapper responseWrapper = new ResponseWrapper((HttpServletResponse) response);
        // 获取request的相关数据
        timeInterval.start();

        sb.append("\n\nURL: " + requestWrapper.getRequestURL().toString()).append("\n");
        sb.append("方法: " + requestWrapper.getMethod()).append("\n");
        if (request.getContentType() != null) {
            sb.append("RequestContentType: " + request.getContentType()).append("\n");
        }
        sb.append("参数: " + requestWrapper.getBody()).append("\n");
        chain.doFilter(requestWrapper, responseWrapper);
        // 获取response的相关数据
//        String contentType = responseWrapper.getContentType();
//        if (contentType.toLowerCase().contains("json")) {//防止数据流被打印出
//            sb.append("响应: " + new String(responseWrapper.getContent(), "UTF-8")).append("\n");
//        } else {
//            sb.append("响应非json不做输出").append("\n");
//        }
        sb.append("ResponseContentType: " + responseWrapper.getContentType()).append("\n");

        sb.append("返回值: " + new String(responseWrapper.getContent(), "UTF-8")).append("\n");
        sb.append("响应码: " + new String(String.valueOf(responseWrapper.getStatus()))).append("\n");
        sb.append("耗时: ").append(timeInterval.intervalRestart()).append("ms").append("\n\n");
        log.debug(sb.toString());

        // 在得到响应的数据之后，response的输出流中就无可用的数据，所以需要巴数据放回去
        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.write(responseWrapper.getContent());
        outputStream.flush();
        outputStream.close();
    }

    @Override
    public void destroy() {
    }
}

class RequestWrapper extends HttpServletRequestWrapper {
    private String body;

    /**
     * Constructs a request object wrapping the given request.
     *
     * @param request The request to wrap
     * @throws IllegalArgumentException if the request is null
     */
    public RequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        // 此处是得到请求内容的长度
        int contentLength = request.getContentLength();
        StringBuilder stringBuilder = new StringBuilder(contentLength);
        BufferedReader bufferedReader = null;
        InputStream inputStream = null;
        try {
            // 输入流获取请求的数据
            inputStream = request.getInputStream();
            if (inputStream != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                char[] charBuffer = new char[1024];
                int bytesRead;
                while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                    stringBuilder.append(charBuffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            throw e;
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (bufferedReader != null) {
                bufferedReader.close();
            }
        }
        // 下面会把数据放回request的输入流中，所以要把数据恢复到与之前请求内容相同的长度
        String s = stringBuilder.toString();
        if (StrUtil.isNotEmpty(s)) {
            try {
                body = String.format("%-" + contentLength + "s", JSONObject.parseObject(stringBuilder.toString()));
            } catch (RuntimeException e) {
                System.out.println("请求参数（非JSON)："+stringBuilder.toString());
                throw e;
            }
        }
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        // 数据放到字节流中
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.getBody().getBytes());
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return false;
            }

            @Override
            public void setReadListener(ReadListener listener) {
            }

            @Override
            public int read() throws IOException {
                // 数据放回request的输入流中
                return byteArrayInputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(this.getInputStream()));
    }

    public String getBody() {
        // 请求中的数据
        return this.body;
    }
}

class ResponseWrapper extends HttpServletResponseWrapper {
    private ByteArrayOutputStream byteArrayOutputStream;
    private ServletOutputStream servletOutputStream;

    /**
     * Constructs a response adaptor wrapping the given response.
     *
     * @param response The response to be wrapped
     * @throws IllegalArgumentException if the response is null
     */
    public ResponseWrapper(HttpServletResponse response) throws IOException {
        super(response);
        byteArrayOutputStream = new ByteArrayOutputStream();
        servletOutputStream = new MyServletOutputStream(byteArrayOutputStream);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return servletOutputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return new PrintWriter(new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8));
    }

    @Override
    public void flushBuffer() {
        if (servletOutputStream != null) {
            try {
                servletOutputStream.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public byte[] getContent() {
        flushBuffer();
        // response中的数据
        return byteArrayOutputStream.toByteArray();
    }

    class MyServletOutputStream extends ServletOutputStream {
        // 把response输出流中的数据写入字节流中
        private ByteArrayOutputStream byteArrayOutputStream;

        public MyServletOutputStream(ByteArrayOutputStream byteArrayOutputStream) {
            this.byteArrayOutputStream = byteArrayOutputStream;
        }

        @Override
        public boolean isReady() {
            return false;
        }

        @Override
        public void setWriteListener(WriteListener listener) {
        }

        @Override
        public void write(int b) throws IOException {
            byteArrayOutputStream.write(b);
        }
    }
}