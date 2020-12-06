import React, {Component} from 'react';
import Simulation from "./Simulation";
import './style.css';

const DATA = {
    "numNodes" : 4,
    "corruptNodes" : [ 4 ],
    "actions" : {
        "1" : {
            "before" : [ {
                "action" : "message_sent",
                "from" : 1,
                "to" : 2,
                "payload" : {
                    "payload" : {
                        "value" : false,
                        "round" : 0
                    },
                    "signer" : 1,
                    "secret" : "룇膓⟠絸๰⦎伾씍し갈踡Ȣ㝙꣍䁽枝"
                }
            }, {
                "action" : "message_sent",
                "from" : 1,
                "to" : 3,
                "payload" : {
                    "payload" : {
                        "value" : false,
                        "round" : 0
                    },
                    "signer" : 1,
                    "secret" : "룇膓⟠絸๰⦎伾씍し갈踡Ȣ㝙꣍䁽枝"
                }
            }, {
                "action" : "message_sent",
                "from" : 1,
                "to" : 4,
                "payload" : {
                    "payload" : {
                        "value" : false,
                        "round" : 0
                    },
                    "signer" : 1,
                    "secret" : "룇膓⟠絸๰⦎伾씍し갈踡Ȣ㝙꣍䁽枝"
                }
            }, {
                "action" : "state_changed",
                "node" : 1,
                "newState" : {
                    "votes" : [ {
                        "value" : false,
                        "round" : 0
                    } ],
                    "hasSent" : true
                }
            }, {
                "action" : "state_changed",
                "node" : 2,
                "newState" : {
                    "votes" : [ ],
                    "hasSent" : false
                }
            }, {
                "action" : "state_changed",
                "node" : 3,
                "newState" : {
                    "votes" : [ ],
                    "hasSent" : false
                }
            }, {
                "action" : "state_changed",
                "node" : 4,
                "newState" : null
            } ],
            "during" : [ ],
            "after" : [ {
                "action" : "state_changed",
                "node" : 1,
                "newState" : {
                    "votes" : [ {
                        "value" : false,
                        "round" : 0
                    } ],
                    "hasSent" : true
                }
            }, {
                "action" : "state_changed",
                "node" : 2,
                "newState" : {
                    "votes" : [ {
                        "value" : false,
                        "round" : 0
                    } ],
                    "hasSent" : false
                }
            }, {
                "action" : "state_changed",
                "node" : 3,
                "newState" : {
                    "votes" : [ {
                        "value" : false,
                        "round" : 0
                    } ],
                    "hasSent" : false
                }
            }, {
                "action" : "message_sent",
                "from" : 4,
                "to" : 1,
                "payload" : {
                    "payload" : {
                        "value" : true,
                        "round" : 1
                    },
                    "signer" : 4,
                    "secret" : "Ȧ⭨쎇磡㼷뉭㟂桫ꃥ좣፼끉᠇৻뺯톢"
                }
            }, {
                "action" : "message_sent",
                "from" : 4,
                "to" : 2,
                "payload" : {
                    "payload" : {
                        "value" : true,
                        "round" : 1
                    },
                    "signer" : 4,
                    "secret" : "Ȧ⭨쎇磡㼷뉭㟂桫ꃥ좣፼끉᠇৻뺯톢"
                }
            }, {
                "action" : "message_sent",
                "from" : 4,
                "to" : 3,
                "payload" : {
                    "payload" : {
                        "value" : true,
                        "round" : 1
                    },
                    "signer" : 4,
                    "secret" : "Ȧ⭨쎇磡㼷뉭㟂桫ꃥ좣፼끉᠇৻뺯톢"
                }
            } ]
        },
        "2" : {
            "before" : [ ],
            "during" : [ ],
            "after" : [ {
                "action" : "message_sent",
                "from" : 1,
                "to" : 2,
                "payload" : {
                    "payload" : {
                        "value" : true,
                        "round" : 2
                    },
                    "signer" : 1,
                    "secret" : "烨憊ㆎᴩ₲숌丹떏ꚃ뿥亢낋둫넎笽뾻"
                }
            }, {
                "action" : "message_sent",
                "from" : 1,
                "to" : 3,
                "payload" : {
                    "payload" : {
                        "value" : true,
                        "round" : 2
                    },
                    "signer" : 1,
                    "secret" : "烨憊ㆎᴩ₲숌丹떏ꚃ뿥亢낋둫넎笽뾻"
                }
            }, {
                "action" : "message_sent",
                "from" : 1,
                "to" : 4,
                "payload" : {
                    "payload" : {
                        "value" : true,
                        "round" : 2
                    },
                    "signer" : 1,
                    "secret" : "烨憊ㆎᴩ₲숌丹떏ꚃ뿥亢낋둫넎笽뾻"
                }
            }, {
                "action" : "state_changed",
                "node" : 1,
                "newState" : {
                    "votes" : [ {
                        "value" : false,
                        "round" : 0
                    }, {
                        "value" : true,
                        "round" : 1
                    } ],
                    "hasSent" : true
                }
            }, {
                "action" : "state_changed",
                "node" : 2,
                "newState" : {
                    "votes" : [ {
                        "value" : false,
                        "round" : 0
                    }, {
                        "value" : true,
                        "round" : 1
                    } ],
                    "hasSent" : false
                }
            }, {
                "action" : "state_changed",
                "node" : 3,
                "newState" : {
                    "votes" : [ {
                        "value" : false,
                        "round" : 0
                    }, {
                        "value" : true,
                        "round" : 1
                    } ],
                    "hasSent" : false
                }
            } ]
        },
        "3" : {
            "before" : [ ],
            "during" : [ ],
            "after" : [ {
                "action" : "message_sent",
                "from" : 2,
                "to" : 1,
                "payload" : {
                    "payload" : {
                        "value" : true,
                        "round" : 3
                    },
                    "signer" : 2,
                    "secret" : "汆⡀欐ฒ䀡ӛ遪柗⳴䡘᱇ᴘ啈霳ഢ蘖"
                }
            }, {
                "action" : "message_sent",
                "from" : 2,
                "to" : 3,
                "payload" : {
                    "payload" : {
                        "value" : true,
                        "round" : 3
                    },
                    "signer" : 2,
                    "secret" : "汆⡀欐ฒ䀡ӛ遪柗⳴䡘᱇ᴘ啈霳ഢ蘖"
                }
            }, {
                "action" : "message_sent",
                "from" : 2,
                "to" : 4,
                "payload" : {
                    "payload" : {
                        "value" : true,
                        "round" : 3
                    },
                    "signer" : 2,
                    "secret" : "汆⡀欐ฒ䀡ӛ遪柗⳴䡘᱇ᴘ啈霳ഢ蘖"
                }
            }, {
                "action" : "state_changed",
                "node" : 2,
                "newState" : {
                    "votes" : [ {
                        "value" : false,
                        "round" : 0
                    }, {
                        "value" : true,
                        "round" : 1
                    }, {
                        "value" : true,
                        "round" : 2
                    } ],
                    "hasSent" : false
                }
            }, {
                "action" : "state_changed",
                "node" : 3,
                "newState" : {
                    "votes" : [ {
                        "value" : false,
                        "round" : 0
                    }, {
                        "value" : true,
                        "round" : 1
                    }, {
                        "value" : true,
                        "round" : 2
                    } ],
                    "hasSent" : false
                }
            } ]
        },
        "4" : {
            "before" : [ ],
            "during" : [ ],
            "after" : [ {
                "action" : "state_changed",
                "node" : 1,
                "newState" : {
                    "votes" : [ {
                        "value" : false,
                        "round" : 0
                    }, {
                        "value" : true,
                        "round" : 1
                    }, {
                        "value" : true,
                        "round" : 3
                    } ],
                    "hasSent" : true
                }
            }, {
                "action" : "terminated",
                "node" : 1
            }, {
                "action" : "output",
                "node" : 1,
                "output" : true
            }, {
                "action" : "terminated",
                "node" : 2
            }, {
                "action" : "output",
                "node" : 2,
                "output" : true
            }, {
                "action" : "message_sent",
                "from" : 3,
                "to" : 1,
                "payload" : {
                    "payload" : {
                        "value" : true,
                        "round" : 4
                    },
                    "signer" : 3,
                    "secret" : "践円쟓㥈챯著댘腵ᄶꕶ杭뀔䟋虙업Ῠ"
                }
            }, {
                "action" : "message_sent",
                "from" : 3,
                "to" : 2,
                "payload" : {
                    "payload" : {
                        "value" : true,
                        "round" : 4
                    },
                    "signer" : 3,
                    "secret" : "践円쟓㥈챯著댘腵ᄶꕶ杭뀔䟋虙업Ῠ"
                }
            }, {
                "action" : "message_sent",
                "from" : 3,
                "to" : 4,
                "payload" : {
                    "payload" : {
                        "value" : true,
                        "round" : 4
                    },
                    "signer" : 3,
                    "secret" : "践円쟓㥈챯著댘腵ᄶꕶ杭뀔䟋虙업Ῠ"
                }
            }, {
                "action" : "state_changed",
                "node" : 3,
                "newState" : {
                    "votes" : [ {
                        "value" : false,
                        "round" : 0
                    }, {
                        "value" : true,
                        "round" : 1
                    }, {
                        "value" : true,
                        "round" : 2
                    }, {
                        "value" : true,
                        "round" : 3
                    } ],
                    "hasSent" : false
                }
            }, {
                "action" : "terminated",
                "node" : 3
            }, {
                "action" : "output",
                "node" : 3,
                "output" : true
            } ]
        }
    }
}


export default class App extends Component {
    render = () => <Simulation data={DATA}/>;
}