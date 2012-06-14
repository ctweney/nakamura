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

package org.sakaiproject.nakamura.api.activity;

import org.sakaiproject.nakamura.api.lite.content.Content;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

public interface Activity extends Serializable {

  /**
   * @return The internally generated ID of this activity.
   */
  public long getId();

  /**
   * @return The externally generated ID.
   */
  public String getEid();

  public void setEid(String eid);

  public String getParentPath();

  public void setParentPath(String parentPath);

  public String getType();

  public void setType(String type);

  public String getMessage();

  public void setMessage(String message);

  public Date getOccurrenceDate();

  public void setOccurrenceDate(Date occurred);

  public String getActor();

  public void setActor(String actor);

  public Map<String, Serializable> getExtraProperties();

  public void setExtraProperties(Map<String, Serializable> extraProperties);

  public int getVersion();

  public void setVersion(int version);

  public Content toContent();

}
