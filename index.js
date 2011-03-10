var http = require('http')
  , crypto = require('crypto')
  , util = require('util')
  , fs = require('fs')
  , url = require('url')
  , redis = require('redis')
  , httprequest = require('request')

var client = redis.createClient()
  , chain = function chain() {
      var steps = Array.prototype.slice.call(arguments)
        , i = 0
      function cb() {
        if (i === steps.length) return
        steps[++i](cb)
      }
      steps[i](cb)
    }

function DateString(d){
  function pad(n){return n<10 ? '0'+n : n}
  return 'day:'+d.getUTCFullYear()+'-'
         + pad(d.getMonth()+1)+'-'
         + pad(d.getDate())
}

client.on("error", function (err) {
    console.log("Redis connection error to "
                + client.host
                + ":" + client.port + " - " + err);
});

var server = http.createServer(function(request, response) {
  request.connection.setTimeout(30000)

  // POST
  if (request.method === 'POST') {
    var cell, coordinates, key, sha1, data = ""
      , day = DateString(new Date)

    request
    .on('data', function(data) {
      //console.log("postdata: "+ data.toString())
      try {
        cell = JSON.parse(data.toString())
        console.log("cell: ", cell)
        if (!(cell.cid && cell.lac && cell.mcc && cell.mnc))
          throw new Error('Invalid input')
      } catch (e) {
        console.log(e, e.stack)
        response.writeHead(400);
        response.end();
        return
      }
      response.writeHead(202);
      sha1 = crypto.createHash('sha1')
      sha1.update(data);
      key = 'cell:'+sha1.digest('base64')

      client.lpush(day, JSON.stringify(
        { humantime: (new Date).toLocaleString(),
          time: +new Date,
          cell: key }))
      client.exists(key, function(err, exists) {
        if (exists) {
          console.log("found key "+key);
          client.hget(key, 'coordinates', function(err, reply) {
            console.log(new Date, "redis reply: "+reply)
            //coordinates = JSON.parse(reply)
            response.end(reply);
          })
        } else {
          var cellrequest =
            { radio_type: "gsm"
            , address_language: "de_DE"
            , host: "maps.google.com"
            , version: "1.1.0"
            , cell_towers:
              [ { mobile_network_code: cell.cid
                , cell_id: cell.mnc
                , mobile_country_code: cell.lac
                , location_area_code: cell.mcc
                }
              ]
            , request_address: true
            }
          console.log("[0;32msending[0m to google: "
                      + JSON.stringify(cellrequest))
          httprequest(
            { method: 'POST',
              uri: 'http://www.google.com/loc/json',
              //uri: 'http://192.168.43.216:8911/pskill',
              json: true,
              body: JSON.stringify(cellrequest) }
            , function (err, res, body) {
                if (!err) {
                  try {
                    var loc = JSON.parse(body)
                  } catch(e) {
                    console.log(e)
                  }
                  console.log("reply from google"+body);
                  client.hset(key, 'cell', JSON.stringify(cell));
                  client.hset(key, 'coordinates', body)
                  //breakes history
                  //if (!loc.location.address) client.expire(key, 60 * 5)
                  response.end(body)
                } else {
                  console.log('error: ', err);
                }
              }
          )
        }
      })
    })

  // GET
  } else if (request.method === 'GET') {
    var body = "" , day, tripcoords = []
      , path = url.parse(request.url).pathname.split('/')

    console.log(request.headers['x-real-ip']+" requested "+path)

    day = path.length <= 3
    ? DateString(new Date)
    : 'day:'+path.slice(2, 5).join('-')

    client.exists(day, function(err, exists) {
      if (exists) {
        chain(
          function(cb) {
            body += '<?xml version="1.0" encoding="UTF-8"?>\n'
            body += '<kml xmlns="http://www.opengis.net/kml/2.2">\n'
            body += '<Document>\n'
            body += '  <Style id="green">\n'
            body += '    <IconStyle>\n'
            body += '      <Icon>\n'
            body += '      <href>http://maps.google.com/mapfiles/kml/pal4/icon28.png</href>\n'
            body += '      </Icon>\n'
            body += '    </IconStyle>\n'
            body += '  </Style>\n'
            body += '  \n'
            body += '  <Folder>\n'
            body += '    <name>cells</name>\n'
            body += '    <visibility>0</visibility>\n'
            body += '    <open>0</open>\n'
            cb()
          },
          function(cb) {
            client.lrange(day, 0, -1, function(err, reply) {
              var i = 0
              reply.forEach(function(point) {
                var p = JSON.parse(point)
                client.hgetall('cell:'+p.cell, function(err, cell) {
                  //cell = JSON.parse(cell.toString())
                  //console.log(cell)
                  try {
                    var coordinates = JSON.parse(cell.coordinates.toString())
                    //console.log(coordinates)
                    if (coordinates.location
                        && coordinates.location.latitude
                        && coordinates.location.longitude)
                    {
                      var lat = coordinates.location.latitude
                        , lon = coordinates.location.longitude
                      body += '  <Placemark>\n'
                      body += '    <name>'+p.humantime+'</name>\n'
                      body += '    <Point>\n'
                      body += '      <coordinates>' +
                                       lon + ',' + lat + '</coordinates>\n'
                      body += '    </Point>\n'
                      body += '  </Placemark>\n'

                      tripcoords.push({lon: lon, lat: lat, date: p.humantime})
                    }
                  } catch(e) {
                    console.log("mooo "+e, cell, p)
                  }
                  if (++i == reply.length) cb()
                })
              })
            })
          },
          function(cb) {
            body += '  </Folder>\n'
            body += '  <Placemark>\n'
            body += '    <name>path</name>\n'
            body += '    <visibility>0</visibility>\n'
            body += '    <styleUrl>#green</styleUrl>\n'
            body += '    <LineString>\n'
            body += '      <extrude>1</extrude>\n'
            body += '      <tessellate>1</tessellate>\n'
            body += '      <altitudeMode>absolute</altitudeMode>\n'
            body += '      <coordinates>\n'
            tripcoords.forEach(function(c) {
              body += c.lon + ',' + c.lat + '\n'
            })
            body += '      </coordinates>\n'
            body += '    </LineString>\n'
            body += '  </Placemark>\n'
            body += '  <Placemark>\n'
            body += '    <name>latest position</name>\n'
            body += '    <description>'+ tripcoords[0].date +'</description>\n'
            body += '    <styleUrl>#green</styleUrl>\n'
            body += '    <Point>\n'
            body += '      <coordinates>' +
                             tripcoords[0].lon + ',' +
                             tripcoords[0].lat +
                    '      </coordinates>\n'
            body += '    </Point>\n'
            body += '  </Placemark>\n'

            body += '</Document>\n'
            body += '</kml>\n'
            cb()
          },
          function() {
            response.writeHead(200, {
              'Content-Length': body.length,
              'Content-Type': 'application/vnd.google-earth.kml+xml' });
            response.end(body)
          }
        )
          //body += reply.toString()
      } else { // day not found in redis
        response.writeHead(204)
        response.end()
      }
    })
  }
})
server.listen(8910, "127.0.0.1");

