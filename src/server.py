### This file runs the flask server on computer which receives incoming http requests from mobile client app ###
from flask import Flask
from flask import request

app = Flask(__name__)
    
@app.route("/speech", methods=['POST'])
def post_method():
    print("Received Speech: ", list(request.form.to_dict(flat=True).keys())[0])
    return "success"
