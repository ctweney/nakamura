Feature: Tagging pooled content

  Scenario: I want to tag an item
    Given I have logged in as "bob"
    When I create a new file
    Then I tag the file with a single tag
    Then I tag the file with multiple tags


