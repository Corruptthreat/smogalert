package co.ghola.backend.service;

/**
 * Created by macbook on 3/12/16.
 */

import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import co.ghola.backend.entity.AirQualitySample;


public class RssFetcher extends HttpServlet {

    private static AirQualitySampleWrapper api =   AirQualitySampleWrapper.getInstance();

    private List<AirQualitySample> dataStoreList = new ArrayList<>();

    private List<AirQualitySample> rssList = new ArrayList<>();

    private final static String RSS_URL ="http://www.stateair.net/dos/RSS/HoChiMinhCity/HoChiMinhCity-PM2.5.xml";

    private static Logger log = Logger.getLogger(RssFetcher.class.getName());

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        BufferedReader reader = null;

        try {
            URL url = new URL(RSS_URL);
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
        } catch (IOException e) {
            throw new MalformedURLException();
        }

        // Reading the feed
        SyndFeedInput input = new SyndFeedInput();
        try {
            SyndFeed feed = input.build(reader);
            List entries = feed.getEntries();
            Iterator itEntries = entries.iterator();

            while (itEntries.hasNext()) {
                SyndEntry entry = (SyndEntry) itEntries.next();
                AirQualitySample sample = createSampleFromRss(entry.getDescription().getValue());
                if (sample != null && Integer.valueOf(sample.getAqi().trim())!= -999){
                    rssList.add(sample);
                }
            }
        } catch (FeedException | ParseException e) {
            log.info(e.getStackTrace().toString());
        }

        //Persisting samples in Datastore

        dataStoreList = api.getAirQualitySamples(null, 1); //retrieve last 24 hrs only
        Long timeStamp = 0L;
        if (dataStoreList.size()> 0) {
            log.info("latest item in datastore:" + dataStoreList.get(0).getTimestamp().toString());
            timeStamp = dataStoreList.get(0).getTimestamp();
        }

        //Removing duplicates, if any

        ArrayList<AirQualitySample> cleanRssList = prepRssList(rssList, timeStamp);

        /*saving new items in datastore */

        api.addAirQualitySampleList(cleanRssList);

        Iterator<AirQualitySample> itr = cleanRssList.iterator();


    }

    private ArrayList<AirQualitySample> prepRssList(List<AirQualitySample> listWithDuplicates, Long timestamp) {
    /* Set of all attributes seen so far */
        Set<Long> attributes = new HashSet<Long>();
    /* All confirmed duplicates go in here */
        List<AirQualitySample> duplicates = new ArrayList<AirQualitySample>();

        for(AirQualitySample sample : listWithDuplicates) {

            if(attributes.contains(sample.getTimestamp())) {

                duplicates.add(sample);
            }

            attributes.add(sample.getTimestamp());
        }

        /* Clean list without any dups */

        listWithDuplicates.removeAll(duplicates);

        /* keeping new RSS fetched samples that are not in the datastore yet */
        ArrayList<AirQualitySample> rssItemListNotinDataStore = new ArrayList<AirQualitySample>();

        for(AirQualitySample listItem : listWithDuplicates){

            if (listItem.getTimestamp().compareTo(timestamp) > 0 ){
                rssItemListNotinDataStore.add(listItem);
            }
        }

        return rssItemListNotinDataStore;
    }

    private  AirQualitySample createSampleFromRss(String rssStr) throws ParseException{
        String[] arr = rssStr.split(";");
        AirQualitySample sample = null;

        sample = new AirQualitySample(arr[3].trim(), arr[4].trim(), DateUtils.getInstance().dateString2Long(arr[0]));//.withZone(DateTimeZone.forID("Asia/Bangkok")));

        return sample;
    }

}