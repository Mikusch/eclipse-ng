package org.mikusch.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import twitter4j.*;

import java.util.ArrayList;
import java.util.List;

@Service
public class DefaultTwitterService implements TwitterService {

    private static final int MAX_STATUSES_PER_PAGE = 200;

    private final Twitter twitter;

    @Autowired
    public DefaultTwitterService(Twitter twitter) {
        this.twitter = twitter;
    }

    @Override
    public List<Status> getAllStatusesForUser(long userId) throws TwitterException {
        List<Status> statuses = new ArrayList<>();

        var paging = new Paging(1, MAX_STATUSES_PER_PAGE);
        while (true) {
            ResponseList<Status> timeline = twitter.getUserTimeline(userId, paging);
            if (timeline.isEmpty()) {
                break;
            } else {
                statuses.addAll(timeline);
            }
            paging.setPage(paging.getPage() + 1);
        }

        return statuses;
    }
}
