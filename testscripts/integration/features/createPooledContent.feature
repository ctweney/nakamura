Feature: Create pooled content

Scenario: I want to create and modify a file
  Given I have a user called "chris"
  And I have logged in as "chris"
  When I create a new file
  Then I check the properties of the new file
  Then I check the body content of the new file
  Then I change the body content

Scenario: As an anonymous user, I want to create content
  Given I log out
  When I create a new file
  Then Anonymous user cannot post content

Scenario: I want to verify that new content is private to me
  Given I have a user called "alice"
  And I have logged in as "alice"
  When I create a new file
  Then I check the properties of the new file
  Then I log out
  Then I should not be able to view file
  Given I have a user called "bob"
  And I have logged in as "bob"
  Then I should not be able to view file

Scenario: I want to create a piece of pooled content
  Given I have a user called "chris"
  And I have logged in as "chris"
  When I create a new piece of pooled content
  Then I check the properties of the new pooled content

Scenario: I want to create a file with alternative streams
  Given I have a user called "chris"
  And I have logged in as "chris"
  When I create a new file
  Then I create an alternative stream of a file
