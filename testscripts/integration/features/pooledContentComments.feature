Feature: Commenting on pooled content

Scenario: I want to comment on a file
  Given I have a user called "chris"
  And I have logged in as "chris"
  When I create a new file
  When I grant everyone permission to read the file
  Then A brand-new file has no comments on it
  Then Make a bogus comment post
  Then Comment on the file
  Then Check that the comment was posted
  Then Edit an existing comment
  Given I have a user called "alice"
  And I have logged in as "alice"
  Then Edit an existing comment as a non-managing viewer
  When I grant everyone permission to manage the file
  Given I have a user called "alice"
  And I have logged in as "alice"
  Then Edit an existing comment as a manager
  Given I have a user called "chris"
  And I have logged in as "chris"
  Then Delete an existing comment

