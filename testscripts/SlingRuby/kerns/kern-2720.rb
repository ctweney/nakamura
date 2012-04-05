#!/usr/bin/env ruby

require 'rubygems'
require 'bundler'
Bundler.setup(:default)
require 'nakamura/test'
require 'nakamura/file'
include SlingUsers
include SlingFile

class TC_Kern2720Test < Test::Unit::TestCase
  include SlingTest

  def add_activity(url, appid, templateid, messagetext, audience)
    res = @s.execute_post("#{url}.activity.json", {
        "sakai:activity-appid" => appid,
        "sakai:activity-templateid" => templateid,
        "sakai:activityMessage" => messagetext,
        "sakai:activity-audience" => audience
    })
    assert_equal("200", res.code, "Should have added activity")
  end

  def test_audience_param_in_activity_create
    @fm = FileManager.new(@s)
    m = uniqueness()
    manager = create_user("user-manager-#{m}")
    @s.switch_user(manager)
    res = @fm.upload_pooled_file("random-#{m}.txt", "Plain content", "text/plain")
    assert_equal("201", res.code, "Expected to be able to create pooled content")
    uploadresult = JSON.parse(res.body)
    contentid = uploadresult["random-#{m}.txt"]['poolId']
    assert_not_nil(contentid, "Should have uploaded ID")
    contentpath = @s.url_for("/p/#{contentid}")

    add_activity(contentpath, "status", "default", "First activity", [ "alice", "bob" ])

    wait_for_indexer()
    res = @s.execute_get(@s.url_for("/var/search/pool/activityfeed.json"), {
        "p" => "/p/#{contentid}"
    })
    assert_equal("200", res.code, "Should have found activity feed")
    activityfeed = JSON.parse(res.body)
    @log.info("Activity feed is #{activityfeed}")
    # we created 1 activity, but the system also created one of
    # type sakai:activityMessage=CREATED_FILE
    assert_equal(2, activityfeed["total"])
    assert_includes(activityfeed["results"][1]["sakai:activity-audience"], "alice")
    assert_includes(activityfeed["results"][1]["sakai:activity-audience"], "bob")

  end

end
