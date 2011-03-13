var http = require('http')
  , crypto = require('crypto')
  , util = require('util')
  , fs = require('fs')
  , url = require('url')
  , util = require('util')
  , redisClient = require('redis')
  , httprequest = require('request')

var redis = redisClient.createClient()
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

redis.on("error", function (err) {
    console.log("Redis connection error to "
                + redis.host
                + ":" + redis.port + " - " + err);
});

var server = http.createServer(function(request, response) {
  var remoteAddress = request.headers['x-real-ip'] || "0.0.0.0"
  request.connection.setTimeout(3000)

  util.puts(new Date() + "[0;35m "+remoteAddress
              +"[0m  "+request.method+' '+request.url)

  // POST
  if (request.method === 'POST') {
    var cell, cellDigest, coordinates, sha1, data = ""
      , path = url.parse(request.url).pathname.split('/')
      , id = path[3]
      , dayId = DateString(new Date) + ':' + id

    request
    .on('data', function(data) {
      chain(
      function(cb) {
        var key = 'ip:'+remoteAddress
        redis.incr(key)
        redis.expire(key, 60)
        redis.get(key, function(err, reply) {
          if (parseInt(reply) > 60) {
            console.log(key + ' reached API limit')
            response.writeHead(410)
            response.end("API limit reached")
          } else cb()
        })
      },
      function(cb) {
        try {
          cell = JSON.parse(data.toString())
          if (!(cell.cid && cell.lac && cell.mcc && cell.mnc))
            throw new Error('Invalid input')
        } catch (e) {
          console.log( e.message+", error parsing json post data:", data.toString())
          response.writeHead(400)
          response.end()
          return
        }
        response.writeHead(202);
        sha1 = crypto.createHash('sha1')
        sha1.update(data);
        cellDigest = sha1.digest('base64')
        var key = 'cell:'+cellDigest

        redis.sadd('user:'+id+':days', dayId);
        redis.lpush(dayId, JSON.stringify(
          { humantime: (new Date).toLocaleString(),
            time: +new Date,
            cell: cellDigest }))
        redis.exists(key, function(err, exists) {
          if (exists) {
            console.log("found key for "+data.toString());
            redis.hget(key, 'coordinates', function(err, reply) {
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
                    redis.hset(key, 'cell', JSON.stringify(cell));
                    redis.hset(key, 'coordinates', body)
                    //breakes history
                    //if (!loc.location.address) redis.expire(key, 60 * 5)
                    response.end(body)
                  } else {
                    console.log('error: ', err);
                  }
                }
            )
          }
        })
      })
    })

  // GET
  } else if (request.method === 'GET') {
    var body = "" , day, tripcoords = []
      , path = url.parse(request.url).pathname.split('/')
      , id = path[3]

    if (path[4] == 'list') {
      redis.smembers('user:'+id+':days', function(err, reply) {
        if (err) throw err;
        body += "<html><body><ul>"
        reply.forEach(function(key) {
          day = key.split(':')[1]
          body += "<li>"+day+' <a href="http://maps.google.com/maps?f=q&geocode=&q=http:%2F%2Fallan.de%2Floc%2Fid%2F'
            +id+'%2F'+day.replace(/-/g, '%2F')+'">map</a></li>'
        })
        body += "</ul></body></html>"
        response.writeHead(200, {
          'Content-Length': body.length,
          'Content-Type': 'text/html' });
        response.end(body)
      })
    }

    day = path.length <= 4
    ? DateString(new Date) + ':' + id
    : 'day:'+path.slice(4, 7).join('-')+':'+id

    redis.exists(day, function(err, exists) {
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
            redis.lrange(day, 0, -1, function(err, reply) {
              var i = 0
              reply.forEach(function(point) {
                var p = JSON.parse(point)
                redis.hgetall('cell:'+p.cell, function(err, cell) {
                  try {
                    var coordinates = JSON.parse(cell.coordinates.toString())
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

process.on('uncaughtException', function (err) {
  console.log('uncaught exception: ' + err, err.stack);
});

