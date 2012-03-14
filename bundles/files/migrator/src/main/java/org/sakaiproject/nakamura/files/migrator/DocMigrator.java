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
package org.sakaiproject.nakamura.files.migrator;

import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.jsoup.nodes.Element;
import org.sakaiproject.nakamura.api.files.FilesConstants;
import org.sakaiproject.nakamura.api.files.FileMigrationService;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.Session;
import org.sakaiproject.nakamura.api.lite.StorageClientUtils;
import org.sakaiproject.nakamura.api.lite.content.Content;
import org.sakaiproject.nakamura.api.lite.content.ContentManager;
import org.sakaiproject.nakamura.api.resource.lite.LiteJsonImporter;
import org.sakaiproject.nakamura.util.ExtendedJSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.StringWriter;
import java.util.Iterator;
import java.util.Set;

@Service
@Component(enabled = true)
public class DocMigrator implements FileMigrationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(DocMigrator.class);
  protected static final String STRUCTURE_ZERO = FilesConstants.STRUCTURE_FIELD_STEM + "0";

  @Reference
  protected Repository repository;
  private final PageMigrator pageMigrator = new PageMigrator(this);

  protected void processStructure0(JSONObject subtree, JSONObject originalStructure, JSONObject newStructure) throws JSONException {
    Set<String> widgetsUsed = Sets.newHashSet();
    for (Iterator<String> referenceIterator = subtree.keys(); referenceIterator.hasNext();) {
      String reference = referenceIterator.next();
      pageMigrator.processPageReference(subtree, originalStructure, newStructure, widgetsUsed, reference);
    }
    // pruning the widgets at the top level
    for (String widgetId : widgetsUsed) {
      if (newStructure.has(widgetId)) {
        newStructure.remove(widgetId);
      }
    }
  }

  protected JSONObject createNewPageStructure(JSONObject structure0, JSONObject originalDoc) throws JSONException {
    JSONObject newDoc = new JSONObject(originalDoc.toString());
    processStructure0(structure0, originalDoc, newDoc);
    // the finishing touches
    newDoc.put(FilesConstants.SCHEMA_VERSION, 2);
    return newDoc;
  }

  @Override
  public boolean fileContentNeedsMigration(Content content) {
    try {
      return !(content == null || isNotSakaiDoc(content) || schemaVersionIsCurrent(content) || contentHasUpToDateStructure(content));
    } catch (SakaiDocMigrationException e) {
      LOGGER.error("Could not determine requiresMigration with content {}", content.getPath());
      throw new RuntimeException("Could not determine requiresMigration with content " + content.getPath());
    }
  }
  
  protected boolean requiresMigration(JSONObject subtree, Content originalStructure, ContentManager contentManager) throws JSONException {
    boolean requiresMigration = false;
    for (Iterator<String> keysIterator = subtree.keys(); keysIterator.hasNext();) {
      String key = keysIterator.next();
      if (!key.startsWith("_")) {
        JSONObject structureItem = subtree.getJSONObject(key);
        String ref = structureItem.getString("_ref");
        if (!contentManager.exists(originalStructure.getPath() + "/" + ref + "/rows")) {
          return true;
        }
       requiresMigration = requiresMigration(structureItem, originalStructure, contentManager); 
      }
    }
    return requiresMigration;
  }

  private boolean isNotSakaiDoc(Content content) {
    return !content.hasProperty(STRUCTURE_ZERO);
  }

  private boolean contentHasUpToDateStructure(Content content) throws SakaiDocMigrationException {
    Session adminSession = null;
    try {
      adminSession = repository.loginAdministrative();
      JSONObject structure0 = new JSONObject((String)content.getProperty(STRUCTURE_ZERO));
      return !requiresMigration(structure0, content, adminSession.getContentManager());
    } catch (Exception e) {
      throw new SakaiDocMigrationException();
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error(e.getMessage());
        }
      }
    }
  }

  private boolean schemaVersionIsCurrent(Content content) {
    return (content.hasProperty(FilesConstants.SCHEMA_VERSION)
        && StorageClientUtils.toInt(content.getProperty(FilesConstants.SCHEMA_VERSION)) >= CURRENT_SCHEMA_VERSION);
  }

  @Override
  public void migrateFileContent(Content content) {
    if (content == null || !content.hasProperty(STRUCTURE_ZERO)) {
      return;
    }
    LOGGER.debug("Starting migration of {}", content.getPath());
    StringWriter stringWriter = new StringWriter();
    ExtendedJSONWriter stringJsonWriter = new ExtendedJSONWriter(stringWriter);
    Session adminSession = null;
    try {
      ExtendedJSONWriter.writeContentTreeToWriter(stringJsonWriter, content, false,  -1);
      adminSession = repository.loginAdministrative();
      JSONObject newPageStructure = createNewPageStructure(new JSONObject((String) content.getProperty(STRUCTURE_ZERO)), new JSONObject(stringWriter.toString()));

      JSONObject convertedStructure = (JSONObject) convertArraysToObjects(newPageStructure);
      validateStructure(convertedStructure);
      LOGGER.debug("Generated new page structure. Saving content {}", content.getPath());
      LiteJsonImporter liteJsonImporter = new LiteJsonImporter();
      liteJsonImporter.importContent(adminSession.getContentManager(), convertedStructure, content.getPath(), true,
              true, false, adminSession.getAccessControlManager(), Boolean.FALSE);
    } catch (Exception e) {
      LOGGER.error(e.getMessage());
      throw new RuntimeException("Error while migrating " + content.getPath());
    } finally {
      if (adminSession != null) {
        try {
          adminSession.logout();
        } catch (ClientPoolException e) {
          LOGGER.error(e.getMessage());
        }
      }
    }
  }
  
  protected Object convertArraysToObjects(Object json) throws JSONException {
    if (json instanceof JSONObject) {
      JSONObject jsonObject = (JSONObject)json;
      for (Iterator<String> keyIterator = jsonObject.keys(); keyIterator.hasNext();) {
        String key = keyIterator.next();
        if (objectIsArrayOfJSONObject(jsonObject.get(key))) {
          jsonObject.put(key, convertArrayToObject((JSONArray) jsonObject.get(key)));
        } else if (jsonObject.get(key) instanceof JSONObject) {
          jsonObject.put(key, convertArraysToObjects(jsonObject.get(key)));
        }
      }
      return jsonObject;
    } else if (objectIsArrayOfJSONObject(json)) {
      return convertArrayToObject((JSONArray)json);
    } else {
      return json;
    }
  }

  private boolean objectIsArrayOfJSONObject(Object json) throws JSONException {
    return json instanceof JSONArray && ((JSONArray)json).length() > 0 && 
      (((JSONArray)json).get(0) instanceof JSONObject || ((JSONArray)json).get(0) instanceof JSONArray);
  }

  protected JSONObject convertArrayToObject(JSONArray jsonArray) throws JSONException {
    JSONObject arrayObject = new JSONObject();
    for(int i = 0; i < jsonArray.length(); i++) {
      Object value = convertArraysToObjects(jsonArray.get(i));
      arrayObject.put("__array__" + i + "__", convertArraysToObjects(jsonArray.get(i)));
    }
    return arrayObject;
  }

  protected void validateStructure(JSONObject newPageStructure) throws JSONException, SakaiDocMigrationException {
    if (!newPageStructure.has(FilesConstants.SCHEMA_VERSION) || !newPageStructure.has(STRUCTURE_ZERO)) {
      throw new SakaiDocMigrationException();
    }
    LOGGER.debug("new page structure passes validation.");
  }

}