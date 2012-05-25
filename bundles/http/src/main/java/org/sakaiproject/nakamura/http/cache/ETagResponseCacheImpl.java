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
import org.sakaiproject.nakamura.api.http.cache.ETagResponseCache;
import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheManagerService;
import org.sakaiproject.nakamura.api.memory.CacheScope;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

@Component
@Service
public class ETagResponseCacheImpl implements ETagResponseCache {

  @Reference
  protected CacheManagerService cacheManagerService;

  private Cache<String> cache;

  @Activate
  protected void activate(ComponentContext componentContext) throws ServletException {
    cache = cacheManagerService.getCache(ETagResponseCache.class.getName() + "-cache",
        CacheScope.INSTANCE);
  }

  @Override
  public void recordResponse(HttpServletRequest request) {
    String key = buildCacheKey(request);
    if (!cache.containsKey(key)) {
      cache.put(buildCacheKey(request), buildETag(request));
    }
  }

  @Override
  public void invalidate(HttpServletRequest request) {
    cache.remove(buildCacheKey(request));
  }

  @Override
  public String getETag(HttpServletRequest request) {
    return cache.get(buildCacheKey(request));
  }

  private String buildETag(HttpServletRequest request) {
    StringBuilder ret = new StringBuilder(request.getRemoteUser());
    ret.append('-').append(request.getPathInfo()).append('-').append(System.nanoTime());
    return ret.toString();
  }

  String buildCacheKey(HttpServletRequest request) {
    StringBuilder ret = new StringBuilder(request.getRemoteUser());
    ret.append('-').append(request.getPathInfo());
    return ret.toString();
  }

}
