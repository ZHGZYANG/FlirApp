# --coding=utf8--
"""
User Instruction:
* Please put this file under FlirApp/image and then use the following command
to run the code

python csvupdate.py

* Do not put files other than .csv into the folder

"""

import os

def actionforcsv(filename):
    with open(filename,"r") as oldcsv:
        content=oldcsv.read().split(",")
    with open(filename.split(".")[0]+"_matrix.csv","w") as newcsv:
        count=1
        for details in content:
            if count<480:
                count+=1
                newcsv.write(details+',')
                continue
            if count==480:
                count=1
                newcsv.write(details + '\n')

root_path=os.getcwd()
files=[item for item in os.listdir(root_path) if item[-2:]!="py"]
folders=[]
for file in files:
     if not os.path.isdir(file): # not folder
         actionforcsv(file)
         os.remove(file)
         print(file+' done.')
     else:
         folders.append(root_path+'/'+file)

for folder in folders:
    files_=os.listdir(folder)
    for i in files_:
        actionforcsv(folder+'/'+i)
        os.remove(folder+'/'+i)
        print(i+' done.')


