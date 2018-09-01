package com.scmspain.services;

import com.scmspain.entities.Tweet;
import org.springframework.boot.actuate.metrics.writer.Delta;
import org.springframework.boot.actuate.metrics.writer.MetricWriter;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class TweetService {
    private static final String QUERY_LIST_ALL_TWEETS = "SELECT id FROM Tweet AS tweetId WHERE pre2015MigrationStatus<>99 AND discarded = FALSE ORDER BY id DESC";

    private EntityManager entityManager;
    private MetricWriter metricWriter;

    public TweetService(EntityManager entityManager, MetricWriter metricWriter) {
        this.entityManager = entityManager;
        this.metricWriter = metricWriter;
    }

    /**
      Push tweet to repository
      Parameter - publisher - creator of the Tweet
      Parameter - text - Content of the Tweet
      Result - recovered Tweet
    */
    public void publishTweet(String publisher, String text) {

        boolean publisherIsNotNullOrEmpty = publisher != null && publisher.length() > 0;

        if (publisherIsNotNullOrEmpty && tweetIsValid(text)) {
            Tweet tweet = new Tweet();
            tweet.setTweet(text);
            tweet.setPublisher(publisher);

            this.metricWriter.increment(new Delta<Number>("published-tweets", 1));
            this.entityManager.persist(tweet);
        } else {
            throw new IllegalArgumentException("Tweet must not be greater than 140 characters");
        }
    }

    /**
      Recover tweet from repository
      Parameter - id - id of the Tweet to retrieve
      Result - retrieved Tweet
    */
    public Tweet getTweet(Long id) {
      return this.entityManager.find(Tweet.class, id);
    }

    /**
      Recover tweet from repository
      Parameter - id - id of the Tweet to retrieve
      Result - retrieved Tweet
    */
    public List<Tweet> listAllTweets() {
        List<Tweet> result = new ArrayList<Tweet>();
        this.metricWriter.increment(new Delta<Number>("times-queried-tweets", 1));
        TypedQuery<Long> query = this.entityManager.createQuery(QUERY_LIST_ALL_TWEETS, Long.class);
        List<Long> ids = query.getResultList();
        for (Long id : ids) {
            result.add(getTweet(id));
        }
        return result;
    }

    /**
     * Discard a tweet
     * Parameter - id - id of the Tweet to discard
     */
    public void discardTweet(Long id) {
        Tweet tweetToDiscard = getTweet(id);

        if (tweetToDiscard != null) {
            tweetToDiscard.setDiscarded(Boolean.TRUE);

            this.metricWriter.increment(new Delta<Number>("discarded-tweets", 1));
            this.entityManager.persist(tweetToDiscard);
        } else {
            throw new IllegalArgumentException("The selected tweet does not exists");
        }
    }

    private boolean tweetIsValid(String tweet) {
        String linkRegex = "(.*)https?://(.*)";
        String space = " ";

        String tweetWithoutLink = tweet;

        if (tweet.matches(linkRegex)) {
            tweetWithoutLink = Arrays.stream(tweet.split(space))
                    .filter(word -> !word.matches(linkRegex))
                    .collect(Collectors.joining(space));
        }

        boolean tweetIsNotNullOrEmpty = tweetWithoutLink != null && tweetWithoutLink.length() > 0;

        return tweetIsNotNullOrEmpty && tweetWithoutLink.length() < 140;
    }
}
