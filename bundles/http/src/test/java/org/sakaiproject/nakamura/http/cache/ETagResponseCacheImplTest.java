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

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.sakaiproject.nakamura.api.http.cache.ETagResponseCache;
import org.sakaiproject.nakamura.api.memory.Cache;
import org.sakaiproject.nakamura.api.memory.CacheManagerService;
import org.sakaiproject.nakamura.api.memory.CacheScope;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

@RunWith(MockitoJUnitRunner.class)
public class ETagResponseCacheImplTest {

  @Mock
  private HttpServletRequest request;

  @Mock
  private Cache<Object> cache;

  private ETagResponseCacheImpl eTagResponseCache;

  @Before
  public void setup() throws ServletException {
    eTagResponseCache = new ETagResponseCacheImpl();
    eTagResponseCache.cacheManagerService = mock(CacheManagerService.class);
    when(eTagResponseCache.cacheManagerService.getCache(ETagResponseCache.class.getName()+"-cache", CacheScope.INSTANCE)).thenReturn(cache);
    eTagResponseCache.activate(null);

  }

  @Test
  public void recordResponse() {
    when(request.getPathInfo()).thenReturn("/foo/bar/baz");
    when(request.getRemoteUser()).thenReturn("joe");

    when(cache.containsKey(eTagResponseCache.buildCacheKey(request))).thenReturn(false);
    eTagResponseCache.recordResponse(request);
    when(cache.containsKey(eTagResponseCache.buildCacheKey(request))).thenReturn(true);
    eTagResponseCache.recordResponse(request);
    verify(cache, times(2)).containsKey(eTagResponseCache.buildCacheKey(request));
    verify(cache, atMost(1)).put(anyString(), anyString());

    eTagResponseCache.invalidate(request);
    verify(cache).remove(anyString());
  }

}
