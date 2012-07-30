Feature: Create pooled content

Scenario: I want to create and modify a regular piece of content
  Given I have a user called "chris"
  And I have logged in as "chris"
  When I create a new file
  Then I check the properties of the new file
  Then I check the body content of the new file
  Then I change the body content

Scenario: As an anonymous user, I want to create content
  Given I have logged out
  When I create a new file as anonymous
  Then Anonymous user cannot post content


