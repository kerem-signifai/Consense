var svg;
var model;
var NUM_SERVERS = 5;
var RPC_TIMEOUT = 50000;
var RPC_LATENCY = 10000;
var ELECTION_TIMEOUT = 100000;
var renderMessages;
var rules = {};

var util = {};

var protocol = {
  0: [
  "send(1 -> 2, SignedMessage(1, true))"],
  1: [
  "stateChange(2, extr: [true])",
  "send(2 -> 3, SignedMessage(SignedMessage(1, true), 2)"],
  };
  
$(function() {

var makeElectionAlarm = function(model) {
  return model.time + (Math.random() + 1) * ELECTION_TIMEOUT;
};

model = {
  servers: [],
  messages: [],
  time: 0,
  seed: 0,
};

var makeLog = function() {
  var entries = [];
  return {
    entries: entries,
    at: function(index) {
      return entries[index - 1];
    },
    len: function() {
      return entries.length;
    },
    term: function(index) {
      if (index < 1 || index > entries.length) {
        return 0;
      } else {
        return entries[index - 1].term;
      }
    },
    slice: function(startIndexIncl, endIndexExcl) {
      return entries.slice(startIndexIncl - 1, endIndexExcl - 1);
    },
    append: function(entry) {
      entries.push(entry);
    },
    truncatePast: function(index) {
      entries = entries.slice(0, index);
    },
  };
};

util.value = function(v) {
  return function() { return v; };
};

util.circleCoord = function(frac, cx, cy, r) {
  var radians = 2 * Math.PI * (.75 + frac);
  return {
    x: cx + r * Math.cos(radians),
    y: cy + r * Math.sin(radians),
  };
};

util.countTrue = function(bools) {
  var count = 0;
  bools.forEach(function(b) {
    if (b)
      count += 1;
  });
  return count;
};

util.makeMap = function(keys, value) {
  var m = {};
  keys.forEach(function(key) {
    m[key] = value;
  });
  return m;
};

util.mapValues = function(m) {
  return $.map(m, function(v) { return v; });
};

var Server = function(id, peers) {
  return {
    id: id,
    peers: peers,
    state: 'follower',
    term: 1,
    votedFor: null,
    log: makeLog(),
    commitIndex: 0,
    electionAlarm: makeElectionAlarm(model),
    rpcDue:      util.makeMap(peers, 0),
    voteGranted: util.makeMap(peers, false),
    matchIndex:  util.makeMap(peers, 0),
    nextIndex:   util.makeMap(peers, 1),
  };
};

var sendMessage = function(model, message) {
  message.sendTime = model.time;
  message.recvTime = model.time + RPC_LATENCY;
  model.messages.push(message);
};

var sendRequest = function(model, request) {
  request.direction = 'request';
  sendMessage(model, request);
};

var sendReply = function(model, request, reply) {
  reply.from = request.to;
  reply.to = request.from;
  reply.type = request.type;
  reply.direction = 'reply';
  sendMessage(model, reply);
};

(function() {
  for (var i = 1; i <= NUM_SERVERS; i += 1) {
      var peers = [];
      for (var j = 1; j <= NUM_SERVERS; j += 1) {
        if (i != j)
          peers.push(j);
      }
      model.servers.push(Server(i, peers));
  }
})();

svg = $('svg');

var ringSpec = {
  cx: 300,
  cy: 200,
  r: 150,
};

var serverSpec = function(id) {
  var coord = util.circleCoord((id - 1) / NUM_SERVERS,
                               ringSpec.cx, ringSpec.cy, ringSpec.r);
  return {
    cx: coord.x,
    cy: coord.y,
    r: 30,
  };
};

var ring = svg.append(
  $('<circle />')
    .attr('id', 'ring')
    .attr(ringSpec));

model.servers.forEach(function (server) {
  var s = serverSpec(server.id);
  svg.append(
    $('<circle />')
      .addClass('server')
      .attr('id', 'server-' + server.id)
      .attr(s));
});

util.reparseSVG = function() {
  svg.html(svg.html()); // reparse as SVG after adding nodes
};
util.reparseSVG();

var messageSpec = function(from, to, frac) {
  var fromSpec = serverSpec(from);
  var toSpec = serverSpec(to);
  // adjust frac so you start and end at the edge of servers
  var totalDist  = Math.sqrt(Math.pow(toSpec.cx - fromSpec.cx, 2) +
                             Math.pow(toSpec.cy - fromSpec.cy, 2));
  var travel = totalDist - fromSpec.r - toSpec.r;
  frac = (fromSpec.r / totalDist) + frac * (travel / totalDist);
  return {
    cx: fromSpec.cx + (toSpec.cx - fromSpec.cx) * frac,
    cy: fromSpec.cy + (toSpec.cy - fromSpec.cy) * frac,
    r: 5,
  };
};

renderServers = function() {
  model.servers.forEach(function(server) {
    $('#server-' + server.id, svg)
      .attr('class', server.state);
  });
};

renderMessages = function() {
  $('.message', svg).remove();
  model.messages.forEach(function(message) {
    var s = messageSpec(message.from, message.to,
                        (model.time - message.sendTime) /
                        (message.recvTime - message.sendTime));
    if (message.recvTime >= model.time) {
      svg.append(
        $('<circle />')
          .addClass('message')
          .attr(s));
    }
  });
  util.reparseSVG();
};

/* Functions to parse protocol hashmap */

//note: rounds in protocol are zero-indexed, but not when called in executeRound
msgsInRound = function(roundNumber){
  messages = [];
  round_events = protocol[roundNumber];
  for(i = 0; i<round_events.length; i++){
    msg = []; 
    if ((round_events[i].substring(0,4))===("send")){ //if send event
        msg.push(parseInt(round_events[i].charAt(5)));
        msg.push(parseInt(round_events[i].charAt(10)));
        messages.push(msg);
      }
    }
  return messages;
}

/* Functions to execute protocol*/
executeRound = function(model, roundNo){
  if (roundNo == 1 || model.time >= (roundNo-1) * 10000){
  //below is all msgs sent simultaneously within the round:
  var msgs = msgsInRound(roundNo-1); //temp is the messages exchanged in a single round
  var server = (model.servers)[0]; //server var may not be necessary if term/lastLogTerm/lastLogIndex is unnecessary
  for(i = 0; i < msgs.length; i++){
    var fromId = (msgs[i])[0];
    var toId = (msgs[i])[1];
    sendMessage(model, {
        from: fromId, //server.id,
        to: toId, //server.id + 1,
        type: 'RequestVote',
        term: server.term,
        lastLogTerm: server.log.term(server.log.len()),
        lastLogIndex: server.log.len()});
  }
}
}

executeProtocol = function(model) {
  var protocol_keys = Object.keys(protocol);
  protocol_keys.forEach(
    function(round){
      executeRound(model, parseInt(round) + 1);
    }
  );
}


setInterval(function() {
    model.time += 100

    executeProtocol(model)

  renderServers();
  renderMessages();
}, 10);


});
