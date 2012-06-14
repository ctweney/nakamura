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

package org.sakaiproject.nakamura.activity;

import com.google.common.collect.ImmutableMap;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.activity.routing.ActivityRouterManager;
import org.sakaiproject.nakamura.api.activity.Activity;
import org.sakaiproject.nakamura.api.activity.ActivityConstants;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.lite.BaseMemoryRepository;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

public class ActivityServiceImplTest extends Assert {

  ActivityServiceImpl activityService;

  private Repository repository;

  @Before
  public void setup() throws Exception {
    this.activityService = new ActivityServiceImpl();
    this.activityService.eventAdmin = Mockito.mock(EventAdmin.class);
    this.activityService.entityManagerFactory = Mockito.mock(EntityManagerFactory.class);
    this.activityService.activityRouterManager = Mockito.mock(ActivityRouterManager.class);

    repository = new BaseMemoryRepository().getRepository();
    this.activityService.repository = repository;

    final Session adminSession = repository.loginAdministrative();
    adminSession.getAuthorizableManager().createUser("joe", "joe", "joe",
        ImmutableMap.of("name", (Object) "joe"));
    adminSession.logout();

  }

  @Test
  public void testCreateActivityAsAdmin() throws Exception {
    final Session adminSession = repository.loginAdministrative();
    final Session userSession = repository.login("joe", "joe");
    final Session anonSession = repository.login();

    String userID = "alice";
    Map<String, Object> props = new HashMap<String, Object>();
    props.put("someProp", "someVal");

    EntityManager entityManager = Mockito.mock(EntityManager.class);
    Mockito.when(this.activityService.entityManagerFactory.createEntityManager()).thenReturn(
        entityManager);
    Mockito.when(entityManager.getTransaction()).thenReturn(Mockito.mock(EntityTransaction.class));
    Mockito.when(entityManager.find(Mockito.eq(Activity.class), Mockito.anyLong())).thenReturn(
        new ActivityModel("/fake/activity/path", new Date(), null));
    this.activityService.createActivity(adminSession, "/some/arbitrary/path", userID, props);

    Mockito.verify(this.activityService.eventAdmin).postEvent(Mockito.any(Event.class));

    // make sure activity store got created
    String storePath = "/some/arbitrary/path/" + ActivityConstants.ACTIVITY_STORE_NAME;

    // check permissions of activity store    
    adminSession.getAccessControlManager().check(Security.ZONE_CONTENT, storePath,
        Permissions.CAN_ANYTHING);
    userSession.getAccessControlManager().check(Security.ZONE_CONTENT, storePath,
        Permissions.CAN_READ);
    userSession.getAccessControlManager().check(Security.ZONE_CONTENT, storePath,
        Permissions.CAN_WRITE);
    boolean canDelete = false;
    try {
      userSession.getAccessControlManager().check(Security.ZONE_CONTENT, storePath,
          Permissions.CAN_DELETE);
      canDelete = true;
    } catch (AccessDeniedException expected) {
    }
    Assert.assertFalse(canDelete);

    boolean canRead = false;
    try {
      anonSession.getAccessControlManager().check(Security.ZONE_CONTENT, storePath,
          Permissions.CAN_READ);
      canRead = true;
    } catch (AccessDeniedException expected) {
    }
    Assert.assertFalse(canRead);

    boolean canWrite = false;
    try {
      anonSession.getAccessControlManager().check(Security.ZONE_CONTENT, storePath,
          Permissions.CAN_WRITE);
      canWrite = true;
    } catch (AccessDeniedException expected) {
    }
    Assert.assertFalse(canWrite);

    adminSession.logout();
    userSession.logout();
  }

  @Test(expected = IllegalStateException.class)
  public void testCreateActivityAsNonAdminActingAsAnotherUser() throws Exception {
    // make sure a non-admin can't create an activity on behalf of another.
    final Session session = repository.login("joe", "joe");
    this.activityService.createActivity(session, "/some/arbitrary/path", "alice", null);
  }


  @Test
  public void postActivity() {
    Map<String, Object> activityProps = ImmutableMap.<String, Object>of(
        ActivityConstants.PARAM_APPLICATION_ID, "Content",
        ActivityConstants.PARAM_ACTIVITY_TYPE, "pooled content",
        ActivityConstants.PARAM_ACTIVITY_MESSAGE, "UPDATED_FILE");
    this.activityService.postActivity("joe", "/some/path", activityProps);
    Mockito.verify(this.activityService.eventAdmin).postEvent(Matchers.any(Event.class));
  }

  @Test(expected = IllegalArgumentException.class)
  public void postActivityWithNullProps() {
    this.activityService.postActivity("joe", "/some/path", null);
  }

  @Test(expected = IllegalArgumentException.class)
  public void postActivityWithMissingMandatoryProp() {
    Map<String, Object> activityProps = ImmutableMap.<String, Object>of(
        ActivityConstants.PARAM_APPLICATION_ID, "Content");
    this.activityService.postActivity("joe", "/some/path", activityProps);
  }

  @Test
  public void testCreateID() throws UnsupportedEncodingException,
      NoSuchAlgorithmException {
    List<String> ids = new ArrayList<String>();
    for (int i = 0; i < 1000; i++) {
      String s = this.activityService.createId();
      if (ids.contains(s)) {
        org.junit.Assert.fail("This id is already in the list.");
      }
      ids.add(s);
    }
  }

}
