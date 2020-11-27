package com.bkjk.platform.gray;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import com.bkjk.platform.eureka.util.JsonUtil;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import groovy.util.logging.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.bkjk.platform.ribbon.ServerFilter;
import com.netflix.loadbalancer.Server;
import com.netflix.niws.loadbalancer.DiscoveryEnabledServer;
import org.springframework.web.util.ContentCachingRequestWrapper;

@Slf4j
public class GrayServerFilter implements ServerFilter {

    public static final Logger logger = LoggerFactory.getLogger(GrayServerFilter.class);

    @Autowired
    @Qualifier("grayScriptEngine")
    private GrayScriptEngine grayScriptEngine;

    private Map convertRequest(HttpServletRequest request) {
        Map<String, Object> context = new HashMap<>();

        Enumeration<String> hs = request.getHeaderNames();
        Map<String, Object> header = new HashMap<>();
        while (hs.hasMoreElements()) {
            String key = hs.nextElement();
            header.put(key, request.getHeader(key));
        }
        context.put("header", header);

        Enumeration<String> pns = request.getParameterNames();
        Map<String, Object> parameter = new HashMap<>();
        while (pns.hasMoreElements()) {
            String key = pns.nextElement();
            parameter.put(key, request.getParameter(key));
        }
        context.put("parameter", parameter);

        try {
            if (!StringUtils.isEmpty(request.getContentType())
                && request.getContentType().toLowerCase().contains("application/json")
                && request instanceof ContentCachingRequestWrapper) {
                ContentCachingRequestWrapper req = (ContentCachingRequestWrapper)request;
                String json = new String(req.getContentAsByteArray(), Charset.forName("UTF-8"));
                if (JsonUtil.isGoodJson(json)) {
                    context.put("body", json);
                }
            }
        } catch (Throwable ignore) {
            logger.error(ignore.getMessage(), ignore);
        }
        if (!context.containsKey("body")) {
            context.put("body", JsonUtil.toJson(Maps.newHashMap()));
        }
        return context;
    }

    /**
     * 灰度时候，比如
     * 请求header有特定grayversion=v1.0.0，则matchList中的map是match=true，grayversion=v1.0.0，match（）匹配出来的是grayList
     * 请求header没有特定grayversion=v1.0.0，则matchList中的map是match=false，grayversion=v1.0.0 ， match（）匹配出来的是nograyList
     * 这样子，特定的请求才进入灰度服务中。
     * 因为是groovy脚本，请求可以多维度参数，比如用请求中的userid，只要userid的hashcode取模落在【0-x-100】中，
     * 在0-x中的则matchList中的map是match=true，grayversion=v1.0.0，match（）匹配出来的是grayList
     * 在x-100中的则matchList中的map是match=false，grayversion=v1.0.0 ， match（）匹配出来的是nograyList
     * groovy还能实现更多灵活功能，比如多灰度版本
     * 所以总体来说，groovy规则很重要，GrayRulesStore，可以在消费者启动完成前，就要去管理平台获取最新的groovy规则；同时，管理平台修改规则时候，
     * 要负责通知每个服务获取最新groovy规则。
     * @param servers
     * @return
     */
    @Override
    public List<Server> match(List<Server> servers) {
        if (servers == null || servers.size() == 0) {
            return servers;
        }
        ServletRequestAttributes requestAttributes =
            (ServletRequestAttributes)RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            logger.debug("No servletRequestAttributes in current thread. Match all servers");
            return Lists.newArrayList(servers);
        }
        HttpServletRequest request = requestAttributes.getRequest();
        //如果没有配置script，则都匹配
        List<Map<String, String>> matchList = grayScriptEngine.execute(servers, convertRequest(request));
        List<Server> allServers = new ArrayList<>();
        allServers.addAll(servers);
        for (Map<String, String> m : matchList) {
            allServers = match(allServers, m);
        }
        if (allServers.size() == 0) {
            logger.info("No server found");
        }
        return allServers;
    }

    /**
     * matchMap保存key-value： match：true/false ， bkjkgray:v1.0.0灰度版本号 等灰度维度值
     * 比如：
     * 如果不存在bkjkgray，则返回所有服务列表
     * 否则根据bkjkgray:v1.0.0，选择出灰度目标列表 和 非目标列表
     * 再根据match决定，是返回目标列表 还是 非目标列表
     * @param servers
     * @param matchMap
     * @return
     */
    private List<Server> match(List<Server> servers, Map<String, String> matchMap) {
        if (servers == null || servers.size() == 0) {
            logger.info("No server to match");
            return servers;
        }
        Map<String, String> nodeInfo = new HashMap();
        nodeInfo.putAll(matchMap);
        String match = nodeInfo.remove("match");
        if (null == nodeInfo || nodeInfo.isEmpty()) {
            return servers;
        }
        List<Server> grayServerList = servers.stream().filter(server -> {
            if (server instanceof DiscoveryEnabledServer) {
                Map<String, String> meteData = ((DiscoveryEnabledServer)server).getInstanceInfo().getMetadata();
                for (String key : nodeInfo.keySet()) {

                    if (!StringUtils.isEmpty(nodeInfo.get(key))) {
                        //只要key存在，并且value值和server的值不同，就去除
                        if (!nodeInfo.get(key).equals(meteData.get(key))) {
                            logger.debug("Excluded server [{}]",
                                ((DiscoveryEnabledServer)server).getInstanceInfo().getHealthCheckUrl());
                            return false;
                        }
                    }
                }
                //nodeInfo中的key的值都为空，或者， key存在时候，值都匹配。 则是灰度匹配目标
                logger.debug("Matched server [{}]",
                    ((DiscoveryEnabledServer)server).getInstanceInfo().getHealthCheckUrl());
                return true;
            }
            logger.warn("Server {} is not instance of DiscoveryEnabledServer. Exclude it.", server.getHost());
            return false;
        }).collect(Collectors.toList());
        List<Server> nonGrayServerList =
            servers.stream().filter(server -> !grayServerList.contains(server)).collect(Collectors.toList());
        if ("true".equals(match)) {
            if (grayServerList.size() == 0) {
                logger.error("Gray rule match = {}. But no server found. NonGrayServerList = {}. GrayServerList = {}",
                    matchMap, nonGrayServerList, grayServerList);
            }
            return grayServerList;
        } else {
            if (nonGrayServerList.size() == 0) {
                logger.error("Gray rule match = {}. But no server found. NonGrayServerList = {}. GrayServerList = {}",
                    matchMap, nonGrayServerList, grayServerList);
            }
            return nonGrayServerList;
        }
    }

}
