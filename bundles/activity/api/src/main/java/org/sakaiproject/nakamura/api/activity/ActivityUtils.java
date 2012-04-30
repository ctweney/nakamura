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
package org.sakaiproject.nakamura.api.activity;

import com.google.common.collect.Maps;
import org.apache.commons.lang.StringUtils;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.sakaiproject.nakamura.util.LitePersonalUtils;
import org.sakaiproject.nakamura.util.PathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Map;

/**
 *
 */
public class ActivityUtils {

  /**
   * Returns the path to the activity feed for a user.
   *
   * @param user
   * @return
   */
  public static String getUserFeed(String user) {
    return ActivityConstants.PREFIX_ACTIVITY_PATH + LitePersonalUtils.getPrivatePath(user) + "/"
        + ActivityConstants.ACTIVITY_FEED_NAME;
  }

  /**
   * Get the path from an activity id.
   *
   * @param id        The ID for an activity.
   * @param startPath The starting path.
   * @return Given an id '2010-01-21-09-randombit' and startPath '/foo/bar' this will
   *         return '/foo/bar/2010/01/21/09/2010-01-21-09-randombit'.
   */
  public static String getPathFromId(String id, String startPath) {
    String[] hashes = StringUtils.split(id, '-');
    StringBuilder sb;

    if (startPath == null) {
      sb = new StringBuilder();
    } else {
      startPath = PathUtils.normalizePath(startPath);
      sb = new StringBuilder(startPath);
    }

    for (int i = 0; i < (hashes.length - 1); i++) {
      sb.append("/").append(hashes[i]);
    }
    return sb.append("/").append(id).toString();

  }

  public static String getActivityPath(String targetContentPath) {
    return ActivityConstants.PREFIX_ACTIVITY_PATH + PathUtils.toUserContentPath(targetContentPath);
  }

}
