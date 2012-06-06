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

package org.sakaiproject.nakamura.openjpa.example;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Modified;
import org.apache.felix.scr.annotations.Reference;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

@Component
public class JPAExampleComponent {

  private static final Logger LOG = LoggerFactory.getLogger(JPAExampleComponent.class);

  @Reference(target = "(osgi.unit.name=openjpaexample)")
  private EntityManagerFactory entityManagerFactory;

  /**
   * This reference is here ONLY to make sure the data source exists before we
   * try to work with the entity manager factory. It's a nuisance and can be
   * avoided if it is found possible to bring in the configurations to the
   * data source in the same style as can be done for services.<br/>
   * This also allows this service to start/stop in parallel with the data
   * source that does the work.
   */
  //@Reference(target = "(dataSourceName=openjpaexample)")
  //private DataSource dataSource;
  @Activate
  @Modified
  public void activate(ComponentContext componentContext) {
    exercise();
  }

  private void exercise() {

    EntityManager entityManager = null;

    try {
      entityManager = entityManagerFactory.createEntityManager();

      LOG.info("Doing some JPA");
      LOG.info("EM: " + entityManager + "; EMF = " + entityManagerFactory);

      LOG.info("Creating example model");
      ExampleModel model = new ExampleModel();
      model.setProperty("New property");
      // entityManager.getTransaction().begin();
      entityManager.persist(model);
      // entityManager.getTransaction().commit();

      ExampleModel model2 = new ExampleModel();
      model2.setProperty("Different prop");
      entityManager.persist(model2);

      // model should be written to database now.
      ExampleModel modelFromDB = entityManager.find(ExampleModel.class, model.getId());
      LOG.info("Model " + modelFromDB.getId() + " from db: " + modelFromDB);

      ExampleModel model2FromDB = entityManager.find(ExampleModel.class, model2.getId());
      LOG.info("Model2 " + model2FromDB.getId() + " from db: " + model2FromDB);

    } finally {
      if (entityManager != null) {
        entityManager.close();
      }
    }
  }

}
