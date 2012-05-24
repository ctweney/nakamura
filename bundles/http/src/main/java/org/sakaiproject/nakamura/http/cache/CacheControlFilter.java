/**
 * Licensed to the Sakai Foundation (SF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The SF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.sakaiproject.nakamura.http.cache;

import org.apache.commons.lang.StringUtils;
import org.apache.felix.http.api.ExtHttpService;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.commons.osgi.PropertiesUtil;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheManagerService;
import org.sakaiproject.nakamura.api.memory.CacheScope;
import org.sakaiproject.nakamura.util.telemetry.TelemetryCounter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component(immediate = true, metatype = true, enabled = true)
@Properties(value = {
    @Property(name = "service.description", value = "Nakamura Cache-Control Filter"),
    @Property(name = "sakai.cache.paths", value = {
        "dev;maxAge:900",
        "devwidgets;maxAge:900"},
        description = "List of subpaths and max age for all content under subpath in seconds, setting to 0 makes it non cacheing"),
    @Property(name = "sakai.cache.patterns", value = {
        "root;.*(js|css)$;maxAge:900",
        "root;.*html$;maxAge:900",
        "var;^/var/search/public/.*$;maxAge:900",
        "var;^/var/widgets.json$;maxAge:900"},
        description = "List of path prefixes followed by a regex. If the prefix starts with a root: it means files in the root folder that match the pattern."),
    @Property(name = "service.vendor", value = "The Sakai Foundation")})
public class CacheControlFilter implements Filter {

  private static final Logger LOGGER = LoggerFactory.getLogger(CacheControlFilter.class);

  /**
   * map of expiry times for whole subtrees
   */
  private Map<String, Map<String, String>> subPaths;

  /**
   * map of patterns by subtree
   */
  private Map<String, Map<Pattern, Map<String, String>>> subPathPatterns;

  /**
   * list of patterns for the root resources
   */
  private Map<Pattern, Map<String, String>> rootPathPatterns;

  static final String SAKAI_CACHE_PATTERNS = "sakai.cache.patterns";

  static final String SAKAI_CACHE_PATHS = "sakai.cache.paths";

  /**
   * Priority of this filter, higher number means sooner
   */
  @Property(intValue = 5)
  private static final String FILTER_PRIORITY_CONF = "filter.priority";

  @Reference
  protected ExtHttpService extHttpService;

  @Reference
  protected CacheManagerService cacheManagerService;

  private Cache<CachedResponse> cache;

  /**
   * {@inheritDoc}
   *
   * @see javax.servlet.Filter#destroy()
   */
  public void destroy() {
  }

  /**
   * {@inheritDoc}
   *
   * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
   *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
   */
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    HttpServletRequest srequest = (HttpServletRequest) request;
    HttpServletResponse sresponse = (HttpServletResponse) response;
    String path = srequest.getPathInfo();

    Map<String, String> cacheConfig;
    int maxAge = 0;
    if (HttpConstants.METHOD_GET.equals(srequest.getMethod())) {
      cacheConfig = getCacheConfig(path);
      if (cacheConfig != null) {
        String maxAgeString = cacheConfig.get("maxAge");
        if (maxAgeString != null) {
          maxAge = Integer.parseInt(maxAgeString);
        }
      }
    }

    if (!responseWasFiltered(srequest, sresponse, chain, maxAge)) {
      chain.doFilter(request, response);
    }

  }

  private Map<String, String> getCacheConfig(String path) {

    // get the Path and then the first 2 elements (2 so that we can tell if this is root
    // or not
    String[] elements = StringUtils.split(path, "/", 2);

    if (elements.length == 0) { // odd request
      return null;
    } else if (elements.length == 1) { // root request eg /index.html
      for (Entry<Pattern, Map<String, String>> p : rootPathPatterns.entrySet()) {
        if (p.getKey().matcher(path).matches()) {
          return p.getValue();
        }
      }
    } else { // subtree //p/index.html

      // check if there is a subtree with a setting
      Map<String, String> headers = subPaths.get(elements[0]);
      if (headers != null) {
        return headers;
      }

      // or a set of patterns for the subtree
      Map<Pattern, Map<String, String>> patterns = subPathPatterns.get(elements[0]);
      if (patterns != null) {
        for (Entry<Pattern, Map<String, String>> p : patterns.entrySet()) {
          if (p.getKey().matcher(path).matches()) {
            return p.getValue();
          }
        }
      }
    }
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * @see javax.servlet.Filter#init(javax.servlet.FilterConfig)
   */
  public void init(FilterConfig filterConfig) throws ServletException {

  }

  @Activate
  protected void activate(ComponentContext componentContext) throws ServletException {
    @SuppressWarnings("unchecked")
    Dictionary<String, Object> properties = componentContext.getProperties();
    String[] sakaiCachePaths = PropertiesUtil.toStringArray(properties.get(SAKAI_CACHE_PATHS));
    subPaths = new HashMap<String, Map<String, String>>();
    if (sakaiCachePaths != null) {
      for (String sakaiCachePath : sakaiCachePaths) {
        String[] cp = StringUtils.split(sakaiCachePath, ';');
        subPaths.put(cp[0], toMap(1, cp));
      }
    }
    String[] sakaiCachePatternPaths = PropertiesUtil.toStringArray(properties.get(SAKAI_CACHE_PATTERNS));
    subPathPatterns = new HashMap<String, Map<Pattern, Map<String, String>>>();
    if (sakaiCachePatternPaths != null) {
      for (String sakaiCachePatternPath : sakaiCachePatternPaths) {
        String[] cp = StringUtils.split(sakaiCachePatternPath, ';');
        if (subPathPatterns.containsKey(cp[0])) {
          subPathPatterns.get(cp[0]).put(Pattern.compile(cp[1]), toMap(2, cp));
        } else {
          Map<Pattern, Map<String, String>> patternMap = new HashMap<Pattern, Map<String, String>>();
          patternMap.put(Pattern.compile(cp[1]), toMap(2, cp));
          subPathPatterns.put(cp[0], patternMap);
        }
      }
    }
    rootPathPatterns = subPathPatterns.get("root");
    if (rootPathPatterns == null) {
      rootPathPatterns = new HashMap<Pattern, Map<String, String>>();
    }

    int filterPriority = PropertiesUtil.toInteger(properties.get(FILTER_PRIORITY_CONF), 0);

    cache = cacheManagerService.getCache(CacheControlFilter.class.getName() + "-cache",
        CacheScope.INSTANCE);

    extHttpService.registerFilter(this, ".*", null, filterPriority, null);


  }

  @SuppressWarnings({"UnusedParameters", "UnusedDeclaration"})
  @Deactivate
  public void deactivate(ComponentContext componentContext) {
    extHttpService.unregisterFilter(this);
  }

  private Map<String, String> toMap(int starting, String[] cp) {
    Map<String, String> map = new HashMap<String, String>();
    for (int i = starting; i < cp.length; i++) {
      String[] kv = StringUtils.split(cp[i], ":", 2);
      map.put(kv[0], kv[1]);
    }
    return map;
  }

  private boolean responseWasFiltered(HttpServletRequest request, HttpServletResponse response,
                                      FilterChain filterChain, int maxAge) throws IOException, ServletException {
    if (!HttpConstants.METHOD_GET.equals(request.getMethod())) {
      return false; // only GET is ever cacheable
    }
    if (maxAge <= 0) {
      return false;
    }

    CachedResponse cachedResponse = getCachedResponse(request);

    if (cachedResponse != null && cachedResponse.isValid()) {
      TelemetryCounter.incrementValue("http", "CacheControlFilter-hit", getCacheKey(request));
      cachedResponse.replay(response);
      return true;
    }

    FilterResponseWrapper filterResponseWrapper = new FilterResponseWrapper(response, false, false, true);
    filterChain.doFilter(request, filterResponseWrapper);
    filterResponseWrapper.setDateHeader("Expires", System.currentTimeMillis() + (maxAge * 1000L));
    saveCachedResponse(request, filterResponseWrapper.getResponseOperation(), maxAge);
    return true;
  }

  private String getCacheKey(HttpServletRequest request) {
    return request.getPathInfo() + "?" + request.getQueryString();
  }

  private CachedResponse getCachedResponse(HttpServletRequest request) {
    CachedResponse cachedResponse;
    cachedResponse = cache.get(getCacheKey(request));
    if (cachedResponse != null && !cachedResponse.isValid()) {
      cachedResponse = null;
      cache.remove(getCacheKey(request));
    }
    return cachedResponse;
  }

  private void saveCachedResponse(HttpServletRequest request,
                                  OperationResponseCapture responseOperation, int maxAge) {
    try {
      if (responseOperation.canCache()) {
        cache.put(getCacheKey(request), new CachedResponse(responseOperation, maxAge));
        TelemetryCounter.incrementValue("http", "CacheControlFilter-save", getCacheKey(request));
      }
    } catch (IOException e) {
      LOGGER.info("Failed to save response in cache ", e);
    }
  }

}
