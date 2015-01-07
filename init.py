#!/usr/bin/python

import json
import requests
import time
import random

api = 'http://localhost:8000'
headers = {'content-type': 'application/json'}

def post(path, payload={}) :
    r = requests.post(api+path, data=json.dumps(payload), headers=headers)
    if (r.text):
        print json.dumps(json.loads(r.text), indent=2)

def put(path, payload={}) :
    r = requests.put(api+path, data=json.dumps(payload), headers=headers)
    if (r.text):
        print json.dumps(json.loads(r.text), indent=2)

def get(path) :
    r = requests.get(api+path, headers=headers)
    if (r.text):
        print json.dumps(json.loads(r.text), indent=2)

def delete(path) :
    r = requests.delete(api+path, headers=headers)
    if (r.text):
        print json.dumps(json.loads(r.text), indent=2)


generator = {
    "Amsterdam": {'mu':100, 'sigma':20},
    "Rotterdam": {'mu':20,  'sigma':20},
    "Utrecht":   {'mu':40,  'sigma':20},
    "Eindhoven": {'mu':100, 'sigma':10},
    "Arnhem":    {'mu':200, 'sigma':50}
}

def randEvent() :
    account = random.randrange(1000)
    city = random.choice(generator.keys())
    amount = random.gauss(generator[city]['mu'],generator[city]['sigma'])
    event = {'account':'NL'+str(account), 'amount':amount, 'city':city }
    return event

#create the graph
post('/api/actors', {"type":"rest"})
post('/api/actors', {"type":"histogram", "by":"city"})
post('/api/actors', {"type":"zscore","ac": "/user/coral/2","by": "city","field": "amount","score" : 2.0})

#post('/api/bonds',  {"producer":1,"consumer":2})
#post('/api/bonds',  {"producer":1,"consumer":3})

put('/api/actors/1',  {"input":{"trigger":{"in":{"type":"external"}}}})
put('/api/actors/2',  {"input":{"trigger":{"in":{"type":"actor", "source":1}}}})
put('/api/actors/3',  {"input":{"trigger":{"in":{"type":"actor", "source":1}}}})
