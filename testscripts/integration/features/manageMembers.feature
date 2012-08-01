Feature: Managing members in pooled content

Scenario: I want to set the editor of a pooled content item
  Given I have logged in as "bob"
  When I create a new file
  When I grant "alice" permission to edit the file
  Then "alice" has read and write privileges

  Given I have logged in as "alice"
  Then User can write the file
  And User cannot delete the file
  And User cannot see acl

Scenario: I want to set the manager of a pooled content item
  Given I have logged in as "bob"
  When I create a new file
  When I grant "alice" permission to manage the file
  Then "alice" has read write and delete privileges

  Given I have logged in as "alice"
  And User can write the file
  And User can see acl
  And User can delete the file
