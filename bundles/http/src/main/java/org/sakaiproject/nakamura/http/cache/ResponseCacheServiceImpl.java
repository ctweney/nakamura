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

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.servlets.HttpConstants;
import org.sakaiproject.nakamura.api.http.cache.ResponseCacheService;
import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheManagerService;
import org.sakaiproject.nakamura.api.memory.CacheScope;
import org.sakaiproject.nakamura.util.telemetry.TelemetryCounter;

import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
@Service
public class ResponseCacheServiceImpl implements ResponseCacheService {

  @Reference
  protected CacheManagerService cacheManagerService;

  @Override
  public boolean playbackCachedResponse(HttpServletRequest request, HttpServletResponse response,
                                        int maxAge) throws IOException {
    if (!HttpConstants.METHOD_GET.equals(request.getMethod())) {
      return false; // only GET is ever cacheable
    }

    String cacheKey = request.getQueryString() == null ? request.getPathInfo() :
        request.getPathInfo() + "?" + request.getQueryString();

    CachedResponseManager cachedResponseManager = new CachedResponseManager(request, maxAge, getCache());

    if (maxAge > 0) {
      if (cachedResponseManager.isValid()) {
        TelemetryCounter.incrementValue("http", "ResponseCacheService-hit", cacheKey);
        cachedResponseManager.send(response);
        return true;
      }
    }

    response.setDateHeader("Date", System.currentTimeMillis());
    response.setDateHeader("Expires", System.currentTimeMillis() + (maxAge * 1000L));
    FilterResponseWrapper filterResponseWrapper = new FilterResponseWrapper(response, false, false, true);
    cachedResponseManager.save(filterResponseWrapper.getResponseOperation());
    TelemetryCounter.incrementValue("http", "ResponseCacheService-save", cacheKey);
    return false;
  }

  private Cache<CachedResponse> getCache() {
    return cacheManagerService.getCache(CacheControlFilter.class.getName() + "-cache", CacheScope.INSTANCE);
  }


}
