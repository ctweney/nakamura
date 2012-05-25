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
import javax.servlet.http.HttpServletResponse;

@RunWith(MockitoJUnitRunner.class)
public class ETagResponseCacheImplTest {

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private Cache<Object> cache;

  private ETagResponseCacheImpl eTagResponseCache;

  @Before
  public void setup() throws ServletException {
    eTagResponseCache = new ETagResponseCacheImpl();
    eTagResponseCache.cacheManagerService = mock(CacheManagerService.class);
    when(eTagResponseCache.cacheManagerService.getCache(ETagResponseCache.class.getName() + "-cache", CacheScope.INSTANCE)).thenReturn(cache);
    eTagResponseCache.activate(null);
  }

  @Test
  public void recordResponseAndInvalidate() {
    String cat = "TestCat";
    String user = "joe";
    when(request.getPathInfo()).thenReturn("/foo/bar/baz");
    when(request.getRemoteUser()).thenReturn(user);

    when(cache.containsKey(eTagResponseCache.buildCacheKey(cat, user))).thenReturn(false);
    eTagResponseCache.recordResponse(cat, request, response);
    when(cache.get(eTagResponseCache.buildCacheKey(cat, user))).thenReturn("foo");
    eTagResponseCache.recordResponse(cat, request, response);
    verify(cache, times(2)).get(anyString());
    verify(cache, atMost(1)).put(anyString(), anyString());
    verify(response, times(2)).setHeader(anyString(), anyString());
    eTagResponseCache.invalidate(cat, user);
    verify(cache).remove(anyString());
  }

  @Test
  public void clientHasFreshETag() {
    when(request.getHeader("If-None-Match")).thenReturn("myetag");
    when(cache.get(anyString())).thenReturn("myetag");
    Assert.assertTrue(eTagResponseCache.clientHasFreshETag("cat", request, response));
    verify(response).setStatus(304);
  }

  @Test
  public void clientLacksETag() {
    when(request.getHeader("If-None-Match")).thenReturn(null);
    when(cache.get(anyString())).thenReturn("myetag");
    Assert.assertFalse(eTagResponseCache.clientHasFreshETag("cat", request, response));
    verify(response, never()).setStatus(304);
  }

  @Test
  public void clientHasOldETag() {
    when(request.getHeader("If-None-Match")).thenReturn("oldetag");
    when(cache.get(anyString())).thenReturn("myetag");
    Assert.assertFalse(eTagResponseCache.clientHasFreshETag("cat", request, response));
    verify(response, never()).setStatus(304);
  }

}
