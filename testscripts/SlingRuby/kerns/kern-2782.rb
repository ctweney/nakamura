#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'nakamura/file'
include SlingUsers
include SlingFile

class TC_Kern2782Test < Test::Unit::TestCase
  include SlingTest

  def setup
    super
    @POOLED_CONTENT_ACTIVITY_FEED = "/var/search/activity/pooledcontent.tidy.json"
  end

  def get_activities(poolid)
    res = @s.execute_get(@s.url_for(@POOLED_CONTENT_ACTIVITY_FEED), {
      "p" => "/p/#{poolid}"
    })
    @log.info("Activity feed is #{res.body}")
    assert_equal("200", res.code, "Should have found activity feed")
    return JSON.parse(res.body)
  end

  def test_pooled_content_activities
    @fm = FileManager.new(@s)
    m = uniqueness()
    manager = create_user("user-manager-#{m}")

    @s.switch_user(User.admin_user())

    res = @fm.upload_pooled_file("random-#{m}.txt", "Plain content", "text/plain")
    assert_equal("201", res.code, "Expected to be able to create pooled content")
    uploadresult = JSON.parse(res.body)
    contentid = uploadresult["random-#{m}.txt"]['poolId']
    assert_not_nil(contentid, "Should have uploaded ID")
    contentpath = @s.url_for("/p/#{contentid}")
    @log.info("Content path = #{contentpath}")

    # modify metadata and make sure user can write it
    res = @s.execute_post("#{contentpath}.html", { "testing" => "testvalue" })
    assert_equal("200", res.code, " #{manager.name} should have been granted write to #{contentpath} ")
    @log.info(" #{manager.name} can write to the resource ")
    res = @s.execute_get("#{contentpath}.tidy.json")
    assert_equal("200", res.code, "Unable to get the metadata for the resource ")
    m = JSON.parse(res.body)
    assert_equal(m["testing"], "testvalue", "Looks like the property was not written Got #{res.body}")

    # waiting for osgi events to fire and for solr index to rebuild
    sleep(5)
    wait_for_indexer()

    activityfeed = get_activities(contentid)
    # the system should have create created one activity
    # sakai:activityMessage=CREATED_FILE
    assert_equal(1, activityfeed["total"])
    assert_equal("CREATED_FILE", activityfeed["results"][0]["sakai:activityMessage"])

    # now update the previous file, should produce another activity of type
    # sakai:activityMessage=UPDATED_FILE
    res = @fm.upload_pooled_file("random-#{m}.txt", "Plain content", "text/plain", contentid)
    assert_equal("200", res.code, "Expected to be able to update pooled content")

    # waiting for osgi events to fire and for solr index to rebuild - is there a smarter way???
    sleep(5)
    wait_for_indexer()

    activityfeed = get_activities(contentid)
    assert_equal(2, activityfeed["total"])
    assert_equal("UPDATED_FILE", activityfeed["results"][0]["sakai:activityMessage"])
    assert_equal("CREATED_FILE", activityfeed["results"][1]["sakai:activityMessage"])

    # now make a comment
    res = @s.execute_post("#{contentpath}.comments", { "comment" => "test1" })
    assert_equal("201", res.code, "Comment should have been created")
    commentcreateresult = JSON.parse(res.body)
    commentid = commentcreateresult["commentId"]

    # waiting for osgi events to fire and for solr index to rebuild - is there a smarter way???
    sleep(5)
    wait_for_indexer()

    commentfeed = get_activities(commentid)
    assert_equal(1, commentfeed["total"])
    assert_equal("CREATED_COMMENT", commentfeed["results"][0]["sakai:activityMessage"])

  end

end
