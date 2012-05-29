/*
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

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.sakaiproject.nakamura.api.http.cache.DynamicContentResponseCache;
import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheManagerService;
import org.sakaiproject.nakamura.api.memory.CacheScope;
import org.sakaiproject.nakamura.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@Service
public class DynamicContentResponseCacheImpl implements DynamicContentResponseCache {

  @Reference
  protected CacheManagerService cacheManagerService;

  private Cache<String> cache;

  @SuppressWarnings("UnusedParameters")
  @Activate
  protected void activate(ComponentContext componentContext) throws ServletException {
    cache = cacheManagerService.getCache(DynamicContentResponseCache.class.getName() + "-cache",
        CacheScope.INSTANCE);
  }

  @Override
  public void recordResponse(String cacheCategory, HttpServletRequest request, HttpServletResponse response) {
    String key = buildCacheKey(cacheCategory, request.getRemoteUser());
    String etag = cache.get(key);
    if (etag == null) {
      etag = buildETag(request);
      cache.put(key, etag);
    }
    response.setHeader("ETag", etag);
  }

  @Override
  public void invalidate(String cacheCategory, String userID) {
    cache.remove(buildCacheKey(cacheCategory, userID));
  }

  @Override
  public boolean clientHasFreshETag(String cacheCategory, HttpServletRequest request, HttpServletResponse response) {
    // examine client request for If-None-Match http header. compare that against the etag.
    String clientEtag = request.getHeader("If-None-Match");
    String serverEtag = cache.get(buildCacheKey(cacheCategory, request.getRemoteUser()));
    if (clientEtag != null && clientEtag.equals(serverEtag)) {
      response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
      return true;
    }
    return false;
  }

  private String buildETag(HttpServletRequest request) {
    String rawTag = request.getRemoteUser() + ':' + request.getPathInfo() + ':' + request.getQueryString()
        + ':' + System.nanoTime();
    try {
      return StringUtils.sha1Hash(rawTag);
    } catch (UnsupportedEncodingException e) {
      return rawTag;
    } catch (NoSuchAlgorithmException e) {
      return rawTag;
    }
  }

  String buildCacheKey(String cacheCategory, String userID) {
    return userID + ':' + cacheCategory;
  }

}
