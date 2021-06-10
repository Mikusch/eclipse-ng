package org.mikusch.service;

import twitter4j.Status;
import twitter4j.TwitterException;

import java.util.List;

public interface TwitterService {

    List<Status> getAllStatusesForUser(long userId) throws TwitterException;
}
