/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.baremaps.iploc;


import com.google.common.net.InetAddresses;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import net.ripe.ipresource.IpResourceRange;
import org.apache.baremaps.geocoder.GeonamesQueryBuilder;
import org.apache.baremaps.utils.IsoCountriesUtils;
import org.apache.lucene.search.SearcherManager;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generating pairs of IP address ranges and their locations into an SQLite database */
public class IpLocMapper implements Function<NicObject, Optional<IpLocObject>> {

  private static final Logger logger = LoggerFactory.getLogger(IpLocMapper.class);

  private final float SCORE_THRESHOLD = 0.1f;

  private final SearcherManager searcherManager;

  /**
   * Constructs an IpLocMapper with the specified geocoder used to find the locations of the
   * objects.
   *
   * @param searcherManager the geocoder that will be used to find the locations of the objects
   */
  public IpLocMapper(SearcherManager searcherManager) {
    this.searcherManager = searcherManager;
  }

  /**
   * Returns an {@code Optional} containing the {@code IpLocObject} associated with the specified
   * {@code NicObject} if it is an inetnum object, or an empty {@code Optional} otherwise.
   *
   * @param nicObject the {@code NicObject}
   * @return an {@code Optional} containing the {@code IpLocObject} corresponding to the
   *         {@code NicObject}
   */
  @Override
  public Optional<IpLocObject> apply(NicObject nicObject) {
    try {
      if (nicObject.attributes().isEmpty()) {
        return Optional.empty();
      }

      if (!NicUtils.isInetnum(nicObject)) {
        return Optional.empty();
      }

      var inetnum = nicObject.attributes().get(0);
      var ipRange = IpResourceRange.parse(inetnum.value());
      var start = InetAddresses.forString(ipRange.getStart().toString());
      var end = InetAddresses.forString(ipRange.getEnd().toString());
      var inetRange = new InetRange(start, end);

      var attributes = nicObject.toMap();

      // Use a default name if there is no netname
      var network = attributes.getOrDefault("netname", "unknown");

      // If there is a geoloc field, we use the latitude and longitude provided
      if (attributes.containsKey("geoloc")) {
        var location = stringToCoordinate(attributes.get("geoloc"));
        if (location.isPresent()) {
          return Optional.of(new IpLocObject(
              attributes.get("geoloc"),
              inetRange,
              location.get(),
              network,
              attributes.get("country")));
        }
      }

      // If there is an address we use that address to query the geocoder
      if (attributes.containsKey("address")) {
        var location = findLocation(attributes.get("address"), attributes.get("country"));
        if (location.isPresent()) {
          return Optional.of(new IpLocObject(
              attributes.get("address"),
              inetRange,
              location.get(),
              network,
              attributes.get("country")));
        }
      }

      // If there is a description we use that description to query the geocoder
      if (attributes.containsKey("descr")) {
        var location = findLocation(attributes.get("descr"), attributes.get("country"));
        if (location.isPresent()) {
          return Optional.of(new IpLocObject(
              attributes.get("descr"),
              inetRange,
              location.get(),
              network,
              attributes.get("country")));
        }
      }

      // If there is a name we use that name to query the geocoder
      if (attributes.containsKey("name")) {
        var location = findLocation(attributes.get("name"), attributes.get("country"));
        if (location.isPresent()) {
          return Optional.of(new IpLocObject(attributes.get("name"),
              inetRange,
              location.get(),
              network, attributes.get("country")));
        }
      }

      // If there is a country that follows the ISO format we use that country's actual name from
      // the iso country map to query the geocoder
      if (attributes.containsKey("country")
          && IsoCountriesUtils.containsCountry(attributes.get("country").toUpperCase())) {
        var countryUppercase = attributes.get("country").toUpperCase();
        var location =
            findLocation(IsoCountriesUtils.getCountry(countryUppercase), countryUppercase);
        if (location.isPresent()) {
          return Optional.of(new IpLocObject(
              IsoCountriesUtils.getCountry(countryUppercase),
              inetRange,
              location.get(),
              network,
              countryUppercase));
        }
      }

      // If there is a country that did not follow the ISO format we will query using the country
      // has plain text
      if (attributes.containsKey("country")) {
        var location = findLocation(attributes.get("country"), "");
        if (location.isPresent()) {
          return Optional.of(new IpLocObject(
              attributes.get("country"),
              inetRange,
              location.get(),
              network,
              attributes.get("country")));
        }
      }

      return Optional.empty();

    } catch (Exception e) {
      logger.warn("Error while mapping nic object to ip loc object", e);
      logger.warn("Nic object attributes:");
      nicObject.attributes().forEach(attribute -> {
        var name = attribute.name();
        var value = attribute.value();
        if (value.length() > 100) {
          value = value.substring(0, 100).concat("...");
        }
        logger.warn("  {} = {}", name, value);
      });
      return Optional.empty();
    }
  }

  /**
   * Uses the geocoder to find the location of the specified search terms.
   *
   * @param searchTerms the search terms
   * @param countryCode the country code
   * @return an {@code Optional} containing the location of the search terms
   * @throws IOException if an I/O error occurs
   */
  private Optional<Coordinate> findLocation(String searchTerms, String countryCode)
      throws IOException {
    var indexSearcher = searcherManager.acquire();
    var geonamesQuery =
        new GeonamesQueryBuilder().queryText(searchTerms).countryCode(countryCode).build();

    var topDocs = indexSearcher.search(geonamesQuery, 1);
    if (topDocs.scoreDocs.length == 0) {
      return Optional.empty();
    }

    var scoreDoc = topDocs.scoreDocs[0];
    if (scoreDoc.score < SCORE_THRESHOLD) {
      return Optional.empty();
    }

    var document = indexSearcher.doc(scoreDoc.doc);
    var longitude = document.getField("longitude").numericValue().doubleValue();
    var latitude = document.getField("latitude").numericValue().doubleValue();

    return Optional.of(new Coordinate(longitude, latitude));
  }

  /**
   * Parse the geoloc in the given string and insert it in the database. The given geoloc is
   * represented by two doubles split by a space.
   *
   * @param geoloc the latitude/longitude coordinates in a string
   * @return an optional containing the location
   */
  private Optional<Coordinate> stringToCoordinate(String geoloc) {
    var doubleRegex = "(\\d+\\.\\d+)";
    var pattern = Pattern.compile("^" + doubleRegex + " " + doubleRegex + "$");
    var matcher = pattern.matcher(geoloc);
    if (matcher.find()) {
      double latitude = Double.parseDouble(matcher.group(1));
      double longitude = Double.parseDouble(matcher.group(2));
      return Optional.of(new Coordinate(longitude, latitude));
    }
    return Optional.empty();
  }
}
