const assert = require('assert')
const micro = require('../target/main.js')

let logMessages = []

let callbackFn = null
let fakeLogQueue = {
  listen: (fn) => { if(!callbackFn) callbackFn = fn },
  send: (msg) => { callbackFn(msg) },
  ack: (msg) => { logMessages.push("ACK") },
  reject: (msg) => { logMessages.push("REJECT") },
  logMessage: (logger, msg) => { logger.info("Processing", {payload: msg.payload}) }
}

let fakeLogger = {
  log: (message, type, data) => {
    logMessages.push({message: message, type: type, data: data})
  }
}


let subscribe = micro.subscribeWith({
  fakeLogQueue: () => micro.implementIO(fakeLogQueue),
  logger: () => micro.implementLogger(fakeLogger)
})

describe('Microscope core components', () => {
  beforeEach( () => {
    logMessages = []
  })

  it('allows to define IO components', (done) => {
    subscribe("fakeLogQueue", (promise) => {
      promise.then(msg => {
        assert.equal("some message", msg.payload)

        assert.equal("Processing", logMessages[0].message)
        assert.equal("some message", logMessages[0].data.payload)
        assert.equal("info", logMessages[0].type)
        assert.equal("ACK", logMessages[1])
        done()
      })
    })

    fakeLogQueue.send({payload: "some message"})
  })
})
